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
  ): Seq[QExpr] = {
    if (tokenOps.exists(_.isInstanceOf[RemoveToken])) {
      require(tokenOps.size == 1)
      Seq()
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
        Seq(newOp)
      } else {
        val repeatOp = current match {
          case qr: QRepeating => qr
          case _ => throw new IllegalArgumentException()
        }
        val (setRepeatedOps, setRepetitions) = otherOps.partition(_.isInstanceOf[SetRepeatedToken])
        if (setRepeatedOps.nonEmpty) {
          require(leafOps.size == 0)
          require(setRepetitions.size <= 1)
          require(setRepetitions.forall(_.isInstanceOf[SetMax]))
          val castSetROps = setRepeatedOps.asInstanceOf[Iterable[SetRepeatedToken]]
          val maxIndex = castSetROps.map(_.index).max
          var prev = 0
          val newExpression = castSetROps.toSeq.sortBy(_.index).map { op =>
            val nextTokens = if (prev == op.index - 1) {
              Seq(op.qexpr)
            } else if (prev == op.index - 2) {
              Seq(repeatOp.qexpr, op.qexpr)
            } else {
              val repeats = op.index - prev - 1
              Seq(QRepetition(repeatOp.qexpr, repeats, repeats), op.qexpr)
            }
            prev = op.index
            nextTokens
          }.flatten
          val newMax = if (setRepetitions.isEmpty) {
            repeatOp.max
          } else {
            setRepetitions.head.asInstanceOf[SetMax].max
          }
          val adjustedMax = Math.max(newMax - maxIndex, -1)
          val adjustedMin = Math.max(repeatOp.min - maxIndex, 0)
          newExpression ++
            QueryLanguage.convertRepetition(QRepetition(repeatOp.qexpr, adjustedMin,
              adjustedMax))
        } else {
          val newChild = repeatOp match {
            case QStar(expr) => changeLeaf(leafOpsCast, expr)
            case QPlus(expr) => changeLeaf(leafOpsCast, expr)
            case QRepetition(expr, _, _) => changeLeaf(leafOpsCast, expr)
          }
          val (min, max) =
            otherOps.foldLeft((repeatOp.min, repeatOp.max)) {
              case ((_, curMax), op: SetMin) => (op.min, curMax)
              case ((curMin, _), op: SetMax) => (curMin, op.max)
              case _ => throw new RuntimeException("Should only have SetMin and SetMax ops left")
            }
          (min, max, repeatOp) match {
            case (0, 0, _) => Seq()
            case (1, 1, _) => Seq(newChild)
            case (0, -1, QPlus(_)) => Seq(QStar(newChild))
            case (1, -1, QStar(_)) => Seq(QPlus(newChild))
            case (_, _, qr: QRepeating) => Seq(QRepetition(newChild, min, max))
          }
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
    var newQuerySequence =
      query.getSeq.zipWithIndex.map {
        case (qexpr, i) =>
          modifierOps.getOrElse(i + 1, Seq(qexpr))
      }

    // Now chunk up our new token sequence, this is done by consuming tokens from our sequence in
    // the same proportions as found in the old query
    var queryTokenSequences = List[QueryTokenSequence]()
    query.tokenSequences.foreach { tokenSeq =>
      val seqSize = tokenSeq.size
      val (newSeq, rest) = newQuerySequence.splitAt(seqSize)
      newQuerySequence = rest
      queryTokenSequences =
        QueryTokenSequence(newSeq.flatten, tokenSeq.columnName) :: queryTokenSequences
    }
    require(newQuerySequence.isEmpty)

    // Finally, add in the new prefix and suffix sequences
    queryTokenSequences = QueryTokenSequence(suffixSeq, None) :: queryTokenSequences
    queryTokenSequences = QueryTokenSequence(prefixSeq, None) :: queryTokenSequences.reverse
    TokenizedQuery(queryTokenSequences)
  }
}

/** Abstract class for classes that combine a set of QueryOps, while keeping track of the number
  * of required edits the collective operations made to each sentence
  */
abstract class CompoundQueryOp() {

  /** @return set of query-token operations to apply to the query */
  def ops: Set[TokenQueryOp]

  /** @return  Map of (sentence index) -> (number of required edits this combined op
    *      will have made towards that sentence)
    */
  def numEdits: IntMap[Int]

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
