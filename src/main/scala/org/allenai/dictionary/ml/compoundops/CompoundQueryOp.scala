package org.allenai.dictionary.ml.compoundops

import org.allenai.dictionary._
import org.allenai.dictionary.ml.TokenizedQuery
import org.allenai.dictionary.ml.primitveops._

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

  // Given a sequence of TokenQueryOps, assumed to apply to the same slot, and
  // optionally the original QExpr in that slot, builds a new QExpr that is the
  // result of apply all the given operators to that token.
  private def buildNewToken(
    tokenOps: Iterable[TokenQueryOp],
    original: Option[QExpr]
  ): QExpr = {
    require(tokenOps.size > 0)
    val allExpressions = tokenOps.map {
      case SetToken(_, q) => q
      case AddToken(_, q) => q
    }.toList
    if (!tokenOps.forall(_.isInstanceOf[SetToken])) {
      assert(original.isDefined)
      QDisj(original.get :: allExpressions)
    } else {
      allExpressions match {
        case x :: Nil => x
        case _ => QDisj(allExpressions)
      }
    }
  }

  /** Applies a sequence of TokenQueryOps to a query, building a new query
    *
    * @param query to transform
    * @param ops to apply to the query
    * @return the transformed query
    */
  def applyOps(
    query: TokenizedQuery,
    ops: Iterable[TokenQueryOp]
  ): TokenizedQuery = {

    val grouped = ops.groupBy(op => op.slot)

    val prefixOps = grouped.flatMap {
      case (slot, qexprs) =>
        slot match {
          case Prefix(token) => Some((token, buildNewToken(qexprs, None)))
          case _ => None
        }
    }
    val prefixSeq = buildExpression(prefixOps).reverse

    val suffixOps = grouped.flatMap {
      case (slot, qexprs) =>
        slot match {
          case Suffix(token) => Some((token, buildNewToken(qexprs, None)))
          case _ => None
        }
    }
    val suffixSeq = buildExpression(suffixOps)

    val captureOps = grouped.flatMap {
      case (slot, qexprs) =>
        slot match {
          case Match(token) => Some((
            token,
            buildNewToken(qexprs, Some(query.getSeq(token - 1)))
          ))
          case _ => None
        }
    }
    val newSeq =
      // Build new capture group by taking new token if any exist
      // otherwise using the previous tokens
      query.getSeq.zipWithIndex.map {
        case (qexpr, i) => captureOps.getOrElse(i + 1, qexpr)
      }

    val (newLeft, rest) = newSeq.splitAt(query.left.size)
    val (replaceCapture, newRight) = rest.splitAt(query.capture.size)
    TokenizedQuery(
      prefixSeq ++ newLeft,
      replaceCapture,
      newRight ++ suffixSeq
    )
  }
}

/** Abstract class for classes that combine a set of TokenQueryOps into a
  * a single query operation
  *
  * @param ops Set of operations that were combined
  * @param numEdits Map of (sentence index) -> (number of edits this combined op
  *               will have made towards that sentence)
  */
abstract class CompoundQueryOp(
    val ops: Set[TokenQueryOp],
    val numEdits: IntMap[Int]
) {

  def canAdd(op: TokenQueryOp): Boolean

  def add(op: TokenQueryOp, matches: IntMap[Int]): CompoundQueryOp = {
    add(EvaluatedOp(op, matches))
  }

  def add(op: EvaluatedOp): CompoundQueryOp

  def applyOps(query: TokenizedQuery): TokenizedQuery = {
    CompoundQueryOp.applyOps(query, ops)
  }

  override def toString: String = {
    if (ops.size == 0) "<>" else {
      val maxReplace = (1 +: ops.flatMap(op => op.slot match {
        case Match(token) => Some(token)
        case _ => None
      }).toSeq).max

      val fakeQexpr = QSeq(Range(0, maxReplace).map(_ => QWildcard()))
      val modified = applyOps(TokenizedQuery.buildFromQuery(QUnnamed(fakeQexpr)))
      "<" + QueryLanguage.getQueryString(modified.getQuery) + ">"
    }
  }

  def toString(qexpr: QExpr): String = {
    "<" + QueryLanguage.getQueryString(applyOps(
      TokenizedQuery.buildFromQuery(qexpr)
    ).getQuery) + ">"
  }
}
