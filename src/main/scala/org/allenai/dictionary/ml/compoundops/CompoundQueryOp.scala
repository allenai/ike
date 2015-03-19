package org.allenai.dictionary.ml.compoundops

import org.allenai.dictionary._
import org.allenai.dictionary.ml.TokenizedQuery
import org.allenai.dictionary.ml.primitveops._

import scala.collection.immutable.IntMap

object EvaluatedOp {
  def fromList(op: TokenQueryOp, matches: Seq[Int]): EvaluatedOp = {
    EvaluatedOp(op, IntMap(matches.map((_, 0)): _*))
  }

  def fromPairs(op: TokenQueryOp, matches: Seq[(Int, Int)]): EvaluatedOp = {
    EvaluatedOp(op, IntMap(matches: _*))
  }
}

/**
 * TokenQueryOp that is paired with a cache of what sentences
 * inside the Hits object the op was created from this operator matches
 *
 * @param op operator
 * @param matches Map of (sentence index) -> 1, if this operator
 */
case class EvaluatedOp(op: TokenQueryOp, matches: IntMap[Int])

object CompoundQueryOp {

  private def buildExpression(ops: Map[Int, QExpr]): Seq[QExpr] = {
    if (ops.size == 0) (Seq())
    else {
      val maxSlot = ops.keys.max + 1
      (1 until maxSlot).map(ops.getOrElse(_, QWildcard()))
    }
  }

  private def buildNewToken(tokenOps: Iterable[TokenQueryOp],
                            original: Option[QExpr]): QExpr = {
    require(tokenOps.size > 0)
    var allExpressions = tokenOps.map(op => op match {
      case SetToken(_, q) => q
      case AddToken(_, q) => q
    }).toList
    val noSetTokens = !tokenOps.forall(x => x match {
      case SetToken(_, _) => true
      case _ => false
    })
    if (noSetTokens) {
      assert(original.isDefined)
      allExpressions = original.get :: allExpressions
    }
    allExpressions match {
      case x::Nil => x
      case _ => QDisj(allExpressions)
    }
  }

  def applyOps(query: TokenizedQuery,
               ops: Iterable[TokenQueryOp]): TokenizedQuery = {

    val grouped = ops.groupBy(op => op.slot)

    val prefixOps = grouped.flatMap { case (slot, qexprs) =>
      slot match {
        case Prefix(token) => Some((token, buildNewToken(qexprs, None)))
        case _ => None
      }
    }
    val prefixSeq = buildExpression(prefixOps).reverse

    val suffixOps = grouped.flatMap { case (slot, qexprs) =>
      slot match {
        case Suffix(token) => Some((token, buildNewToken(qexprs, None)))
        case _ => None
      }
    }
    val suffixSeq = buildExpression(suffixOps)

    val captureOps = grouped.flatMap { case (slot, qexprs) =>
      slot match {
        case Match(token) => Some((token,
          buildNewToken(qexprs, Some(query.getSeq()(token - 1)))))
        case _ => None
      }
    }
    val replaceSeq =
    // Build new capture group by taking new token if any exist
    // otherwise using the previous tokens
      query.getSeq.zipWithIndex.map{ case (qexpr, i) => {
        captureOps.getOrElse(i + 1, qexpr)
      }}

    val (replaceLeft, rest) = replaceSeq.splitAt(query.left.size)
    val (replaceCapture, replaceRight) = rest.splitAt(query.capture.size)
    TokenizedQuery(
      prefixSeq ++ replaceLeft,
      replaceCapture,
      replaceRight ++ suffixSeq
    )
  }
}

abstract class CompoundQueryOp(val ops: Set[TokenQueryOp],
                               val numEdits: IntMap[Int]) {

  def canAdd(op: TokenQueryOp): Boolean

  def add(op: TokenQueryOp, matches: IntMap[Int]): CompoundQueryOp = {
    add(EvaluatedOp(op, matches))
  }

  def add(op: EvaluatedOp): CompoundQueryOp

  def applyOps(query: TokenizedQuery) = {
    CompoundQueryOp.applyOps(query, ops)
  }

  override def toString(): String = {
    if (ops.size == 0) "<>" else {
      val maxReplace = (1 +: ops.flatMap(op => op.slot match {
        case Match(token) => Some(token)
        case _ => None
      }).toSeq).max

      val fakeQexpr = QSeq(Range(0, maxReplace).map(_ => QWildcard()))
      val modified = applyOps(TokenizedQuery.buildFromQuery(QUnnamed(fakeQexpr)))
      return "<" + QueryLanguage.getQueryString(modified.getQuery()) + ">"
    }
  }

  def toString(qexpr: QExpr): String = {
    return "<" + QueryLanguage.getQueryString(applyOps(
      TokenizedQuery.buildFromQuery(qexpr)).getQuery()) + ">"
  }

  override def hashCode(): Int = ops.hashCode()

  override def equals(obj: scala.Any): Boolean = obj match {
    case c: CompoundQueryOp => ops.equals(c.ops)
    case _ => false
  }

}
