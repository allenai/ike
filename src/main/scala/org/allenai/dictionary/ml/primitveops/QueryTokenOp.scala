package org.allenai.dictionary.ml.primitveops

import org.allenai.dictionary._
import org.allenai.dictionary.ml.{QueryToken, Slot}

/** Operation that changes a single token within a fixed length query
  */
sealed abstract class TokenQueryOp(val slot: Slot)

/** Set a token in a qexpr. Token could be replace an existing token
  * or be added as a suffix or prefix depending on slot
  */
case class SetToken(override val slot: Slot, qexpr: QExpr) extends TokenQueryOp(slot)

/** Adds a token to a query as a disjunction with the existing token */
case class AddToken(index: Int, qexpr: QExpr) extends TokenQueryOp(QueryToken(index))

/** TokenQueryOp that has been marked as required (meaning the query will
  * match the hit only if the operator is used) or not (meaning the query
  * will match the hit whether not the operator is applied).
  */
case class MarkedOp(op: TokenQueryOp, required: Boolean)

