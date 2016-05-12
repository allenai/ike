package org.allenai.ike.ml.queryop

import org.allenai.ike._
import org.allenai.ike.ml.{ QueryToken, Slot }

/** Abstract class for a 'QueryOperation' that can be used to modify a QExpr
  */
sealed abstract class QueryOp()

/** Ways two TokenQueryOps can be combined if they apply to the same Slot. NONE means the ops
  * are incompatible, AND mean the resulting query will match a sentence if both ops matched that
  * sentence and OR means the resulting query will match sentences that either op matched
  */
object TokenCombination extends Enumeration {
  type TokenCombination = Value
  val NONE, AND, OR = Value
}
import org.allenai.ike.ml.queryop.TokenCombination._

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
  override def combinable(other: TokenQueryOp): TokenCombination = NONE
}

/** Changes a top level modifier of a QExpr, such as * or + operators */
sealed abstract class ChangeRepetition extends TokenQueryOp()

/** Changes a QDisj or QLeaf, that is possibly being modifier by a * or + operator */
sealed abstract class ChangeLeaf extends TokenQueryOp {

  def combinable(other: ChangeLeaf): TokenCombination

  def combinable(other: TokenQueryOp): TokenCombination = other match {
    case cl: ChangeLeaf => combinable(cl)
    case cm: ChangeRepetition => AND
    case rt: RemoveToken => NONE
    case str: SetRepeatedToken => NONE
  }
}

object SetMin {
  def apply(index: Int, min: Int): SetMin = {
    SetMin(QueryToken(index), min)
  }
}
case class SetMin(slot: Slot, min: Int) extends ChangeRepetition {
  def combinable(other: TokenQueryOp): TokenCombination = other match {
    case cl: ChangeLeaf => AND
    case cm: SetMax => if (cm.max >= min) AND else NONE
    case cm: SetMin => NONE
    case rt: RemoveToken => NONE
    case str: SetRepeatedToken => NONE
  }
}

object SetMax {
  def apply(index: Int, max: Int): SetMax = {
    SetMax(QueryToken(index), max)
  }
}
case class SetMax(slot: Slot, max: Int) extends ChangeRepetition {
  def combinable(other: TokenQueryOp): TokenCombination = other match {
    case cl: ChangeLeaf => AND
    case cm: SetMin => if (cm.min <= max) AND else NONE
    case cm: SetMax => NONE
    case rt: RemoveToken => NONE
    case str: SetRepeatedToken => if (max >= str.index) AND else NONE
  }
}

object SetRepeatedToken {
  def apply(slotIndex: Int, repetitionIndex: Int, qexpr: QExpr): SetRepeatedToken = {
    SetRepeatedToken(QueryToken(slotIndex), repetitionIndex, qexpr)
  }
}
/** Sets a single token within a QRepetition (ex. ".[1,4]" -> ". NN .[0,2]" */
case class SetRepeatedToken(slot: Slot, index: Int, qexpr: QExpr) extends TokenQueryOp {
  override def combinable(other: TokenQueryOp): TokenCombination = other match {
    case cl: ChangeLeaf => NONE
    case cm: SetMin => NONE
    case cm: SetMax => if (cm.max >= index) AND else NONE
    case rt: RemoveToken => NONE
    case str: SetRepeatedToken => if (str.index == index) NONE else AND
  }
}

/** Set a token in a qexpr. Token could replace an existing token or be added as a suffix or
  * prefix depending on slot. If applied to a QStar or QPlus operator changes the child
  * of that operator
  */
case class SetToken(slot: Slot, qexpr: QExpr) extends ChangeLeaf() {
  override def combinable(other: ChangeLeaf): TokenCombination = {
    if (other != this) {
      other match {
        case AddToken(_, _) => OR
        case SetToken(_, _) => OR
        case _ => NONE
      }
    } else {
      NONE
    }
  }
}

/** Adds a token to a query as a disjunction with the existing token */
object AddToken {
  def apply(index: Int, qexpr: QExpr): AddToken = AddToken(QueryToken(index), qexpr)
}
case class AddToken(slot: QueryToken, qexpr: QExpr) extends ChangeLeaf() {
  override def combinable(other: ChangeLeaf): TokenCombination = {
    if (other != this) {
      other match {
        case AddToken(_, _) => OR
        case SetToken(_, _) => OR
        case _ => NONE
      }
    } else {
      NONE
    }
  }
}

/** Remove a token from an existing disjunction */
object RemoveFromDisj {
  def apply(index: Int, qexpr: QExpr): RemoveFromDisj = RemoveFromDisj(QueryToken(index), qexpr)
}
case class RemoveFromDisj(slot: QueryToken, qexpr: QExpr) extends ChangeLeaf() {
  override def combinable(other: ChangeLeaf): TokenCombination = other match {
    case rd: RemoveFromDisj if rd != this => AND
    case _ => NONE
  }
}
