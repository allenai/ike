package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary._
import org.allenai.dictionary.ml.{ QueryToken, Slot }

/** Abstract class for a 'QueryOperation' that can be used to modify a QExpr
  */
sealed abstract class QueryOp()

/** Remove a token as long as it is an 'edge', meaning all tokens before or after it have
  * also been removed
  */
case class RemoveEdge(index: Int, edge: Int) extends QueryOp {

  /** @return which tokens need to be removed from the starting query before this can be applied */
  def afterRemovals: Range = if (edge < index) {
    Range(edge, index)
  } else {
    Range.inclusive(edge, index + 1, -1)
  }
}

/** Ways two TokenQueryOps can be combined if they apply to the same Slot. NONE means the ops
  * are incompatible, AND mean the resulting query will match a sentence if both ops matched that
  * sentence and OR means the resulting query will match sentences that either op matched
  */
object TokenCombination extends Enumeration {
  type TokenCombination = Value
  val NONE, AND, OR = Value
}
import TokenCombination._

/** Operation that changes a single query-token within a TokenizedQuery
  */
sealed abstract class TokenQueryOp() extends QueryOp {
  def slot: Slot
  def combinable(other: TokenQueryOp): TokenCombination
}

object RemoveToken {
  def apply(index: Int): RemoveToken = RemoveToken(QueryToken(index))
}

/** Remove the query-token at the specified slot */
case class RemoveToken(slot: QueryToken) extends TokenQueryOp() {
  def combinable(other: TokenQueryOp): TokenCombination = NONE
}

/** Changes a top level modifier of a QExpr, such as * or + operators */
sealed abstract class ChangeModifier extends TokenQueryOp()

/** Changes a QDisj or QLeaf, that is possibly being modifier by a * or + operator */
abstract class ChangeLeaf extends TokenQueryOp {
  def combinable(other: TokenQueryOp): TokenCombination = other match {
    case cl: ChangeLeaf => OR
    case cm: ChangeModifier => AND
    case rt: RemoveToken => NONE
  }
}

object SetMin {
  def apply(index: Int, min: Int): SetMin = {
    SetMin(QueryToken(index), min)
  }
}
case class SetMin(slot: Slot, min: Int) extends ChangeModifier {
  def combinable(other: TokenQueryOp): TokenCombination = other match {
    case cl: ChangeLeaf => AND
    case cm: SetMax => if (cm.max >= min) AND else NONE
    case cm: SetMin => NONE
    case rt: RemoveToken => NONE
  }
}

object SetMax {
  def apply(index: Int, max: Int): SetMax = {
    SetMax(QueryToken(index), max)
  }
}
/** Changes a top level modifier of a QExpr, such as * or + operators */
case class SetMax(slot: Slot, max: Int) extends ChangeModifier {
  def combinable(other: TokenQueryOp): TokenCombination = other match {
    case cl: ChangeLeaf => AND
    case cm: SetMin => if (cm.min <= max) AND else NONE
    case cm: SetMax => NONE
    case rt: RemoveToken => NONE
  }
}

/** Set a token in a qexpr. Token could replace an existing token or be added as a suffix or
  * prefix depending on slot. If applied to a QStar or QPlus operator changes the child
  * of that operator
  */
case class SetToken(slot: Slot, qexpr: QExpr) extends ChangeLeaf()

/** Adds a token to a query as a disjunction with the existing token */
object AddToken {
  def apply(index: Int, qexpr: QExpr): AddToken = AddToken(QueryToken(index), qexpr)
}
case class AddToken(slot: QueryToken, qexpr: QExpr) extends ChangeLeaf()

/** TokenQueryOp that has been marked as required (meaning the query will
  * match the hit only if the operator is used) or not (meaning the query
  * will match the hit whether not the operator is applied).
  */
case class MarkedOp(op: TokenQueryOp, required: Boolean)
