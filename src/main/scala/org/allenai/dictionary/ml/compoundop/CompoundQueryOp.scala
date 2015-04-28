package org.allenai.dictionary.ml.compoundop

import org.allenai.dictionary._
import org.allenai.dictionary.ml._
import org.allenai.dictionary.ml.queryop._

import scala.collection.immutable.IntMap

object CompoundQueryOp {

  // Transforms a sequence of (index, QExpr) pairs into a sequence of QExpr
  // using QWildcard expressions to fill in 'gaps' in the given indices
  private def buildExpression(ops: Map[Int, QExpr]): Seq[QExpr] = {
    if (ops.size == 0) {
      Seq()
    } else {
      (1 until ops.keys.max + 1).map(ops.getOrElse(_, QWildcard()))
    }
  }

  // Given a sequence of TokenQueryOps, assumed to apply to the same, empty slot, builds
  // a new QExpr that is the result of applying all the given operators to that slot.
  private def buildToken(
    tokenOps: Iterable[TokenQueryOp]
  ): QExpr = {
    require(tokenOps.size > 0)
    val allExpressions = tokenOps.map {
      case SetToken(_, q) => q
      case AddToken(_, q) => q
      case _ => throw new RuntimeException()
    }.toList
    allExpressions match {
      case x :: Nil => x
      case _ => QDisj(allExpressions)
    }
  }

  private def changeLeaf(leafOps: Iterable[ChangeLeaf], current: QExpr): QExpr = {
    if (leafOps.size == 0) {
      current
    } else {
      val allExpressions = leafOps.map {
        case SetToken(_, q) => q
        case AddToken(_, q) => q
        case _ => throw new RuntimeException()
      }.toList
      if (leafOps.exists(_.isInstanceOf[AddToken])) {
        // If there are any AddToken Ops keep the original QExpr
        current match {
          case QDisj(exprs) => QDisj(exprs ++ allExpressions)
          case _ => QDisj(current :: allExpressions)
        }
      } else {
        allExpressions match {
          case x :: Nil => x
          case _ => QDisj(allExpressions)
        }
      }
    }
  }

  // Given a sequence of TokenQueryOps, assumed to apply to the same, filled slot, builds
  // a new QExpr that is the result of apply all the given operators to that slot. Returns
  // None if the existing token should be removed without being replaced
  private def changeToken(
    tokenOps: Iterable[TokenQueryOp],
    current: QExpr
  ): Option[QExpr] = {
    if (tokenOps.exists(_.isInstanceOf[RemoveToken])) {
      require(tokenOps.size == 1)
      None
    } else {
      val (leafOps, otherOps) = tokenOps.partition(_.isInstanceOf[ChangeLeaf])
      val leafOpsCast = leafOps.map(_.asInstanceOf[ChangeLeaf])
      if (otherOps.size == 0) {
        val newOp = current match {
          case QStar(expr) => QStar(changeLeaf(leafOpsCast, expr))
          case QPlus(expr) => QPlus(changeLeaf(leafOpsCast, expr))
          case QRepetition(expr, min, max) => QRepetition(changeLeaf(leafOpsCast, expr), min, max)
          case _ => changeLeaf(leafOpsCast, current)
        }
        Some(newOp)
      } else {
        require(otherOps.size <= 2) // At most a SetMin and a SetMax op
        val newChild = current match {
          case QStar(expr) => changeLeaf(leafOpsCast, expr)
          case QPlus(expr) => changeLeaf(leafOpsCast, expr)
          case QRepetition(expr, _, _) => changeLeaf(leafOpsCast, expr)
          case _ => throw new IllegalArgumentException()
        }
        val repeatOp = current.asInstanceOf[QRepeating]
        val (min, max) =
          otherOps.foldLeft((repeatOp.min, repeatOp.max)) {
            case ((_, curMax), op: SetMin) => (op.min, curMax)
            case ((curMin, _), op: SetMax) => (curMin, op.max)
            case _ => throw new RuntimeException("Should only have SetMin and SetMax ops left")
          }
        (min, max, repeatOp) match {
          case (0, 0, _) => None
          case (1, 1, _) => Some(newChild)
          case (0, -1, QPlus(_)) => Some(QStar(newChild))
          case (1, -1, QStar(_)) => Some(QPlus(newChild))
          case (_, _, qr: QRepeating) => Some(QRepetition(newChild, min, max))
        }
      }
    }
  }

  /** Applies a sequence of TokenQueryOps to a query, building a new, transformed query
    *
    * @param query to transform
    * @param tokenOps to apply to the query
    * @return the transformed query
    */
  def applyOps(
    query: TokenizedQuery,
    tokenOps: Iterable[TokenQueryOp]
  ): TokenizedQuery = {

    val groupedbySlot = tokenOps.groupBy(op => op.slot)
    val prefixOps = groupedbySlot.flatMap {
      case (slot, qexprs) =>
        slot match {
          case Prefix(token) => Some((token, buildToken(qexprs)))
          case _ => None
        }
    }
    val prefixSeq = buildExpression(prefixOps).reverse

    val suffixOps = groupedbySlot.flatMap {
      case (slot, qexprs) =>
        slot match {
          case Suffix(token) => Some((token, buildToken(qexprs)))
          case _ => None
        }
    }
    val suffixSeq = buildExpression(suffixOps)

    val modifierOps = groupedbySlot.flatMap {
      case (slot, ops) =>
        slot match {
          case QueryToken(token) => Some((
            token,
            changeToken(ops, query.getSeq(token - 1))
          ))
          case _ => None
        }
    }

    // Build a new token sequence to be use in our new TokenizedQuery by taking the modified
    // tokens if it exists otherwise using the previous tokens. None means the previous token
    // should be removed and not replaced
    var newSeq =
      query.getSeq.zipWithIndex.map {
        case (qexpr, i) => modifierOps.getOrElse(i + 1, Some(qexpr))
      }

    // Now split up our new token sequence into capture and non capture groups, this is
    // done by consuming tokens from our sequence in the same proportions as found in the old query
    var onCapture = false
    var captures = List[CaptureSequence]()
    var nonCaptures = List[Seq[QExpr]]()
    var onIndex = 0
    var done = false
    while (!done) {
      if (onCapture) {
        val chunkSize = query.captures(onIndex).seq.size
        val (newCaptureSeq, nextSeq) = newSeq.splitAt(chunkSize)
        newSeq = nextSeq
        val newCapture = CaptureSequence(newCaptureSeq.flatten, query.captures(onIndex).columnName)
        captures = newCapture :: captures
        onIndex += 1
      } else {
        val chunkSize = query.nonCaptures(onIndex).size
        val (newPad, nextSeq) = newSeq.splitAt(chunkSize)
        newSeq = nextSeq
        nonCaptures = newPad.flatten :: nonCaptures
        if (onIndex == query.nonCaptures.size - 1) {
          done = true
        }
      }
      onCapture = !onCapture
    }
    nonCaptures = nonCaptures.reverse
    captures = captures.reverse

    // Finally, add in the new prefix and suffix sequences
    nonCaptures = nonCaptures.updated(0, prefixSeq ++ nonCaptures.head)
    nonCaptures = nonCaptures.updated(nonCaptures.size - 1, nonCaptures.last ++ suffixSeq)
    TokenizedQuery(captures, nonCaptures)
  }
}

/** Abstract class for classes that combine a set of QueryOps, while keeping track of the number
  * of required edits the collective operations made to each sentence
  *
  * @param ops set of query-token operations to apply to the query
  * @param numEdits Map of (sentence index) -> (number of required edits this combined op
  *          will have made towards that sentence)
  */
abstract class CompoundQueryOp(
    val ops: Set[TokenQueryOp],
    val numEdits: IntMap[Int]
) {

  def size: Int = ops.size

  def canAdd(op: QueryOp): Boolean

  def add(op: TokenQueryOp, matches: IntMap[Int]): CompoundQueryOp = {
    add(EvaluatedOp(op, matches))
  }

  def add(op: EvaluatedOp): CompoundQueryOp

  def applyOps(query: TokenizedQuery): TokenizedQuery = {
    CompoundQueryOp.applyOps(query, ops)
  }

  override def toString: String = {
    ops.toString()
  }

  def toString(query: TokenizedQuery): String = {
    "<" + QueryLanguage.getQueryString(applyOps(query).getQuery) + ">"
  }
}
