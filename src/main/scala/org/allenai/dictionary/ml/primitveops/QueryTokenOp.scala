package org.allenai.dictionary.ml.primitveops

import org.allenai.dictionary._

/** Indicates a 'slot' relative to a particular query. A Slot indicates a token within the
  * existing query, or an 'empty' token that occurs before of after the existing query
  */
sealed abstract class Slot(token: Int) { require(token >= 1) }

case class Prefix(token: Int) extends Slot(token)
case class Match(token: Int) extends Slot(token)
case class Suffix(token: Int) extends Slot(token)

/** Operation that changes a single token within a fixed length query
  */
sealed abstract class TokenQueryOp(val slot: Slot)

/** Set a token in a qexpr. Token could be replace an existing token
  * or be added as a suffix or prefix depending on slot
  */
case class SetToken(override val slot: Slot, qexpr: QExpr) extends TokenQueryOp(slot)

/** Adds a token to a query as a disjunction with the existing token */
case class AddToken(index: Int, qexpr: QExpr) extends TokenQueryOp(Match(index))

/** TokenQueryOp that has been marked as required (meaning the query will
  * match the hit only if the operator is used) or not (meaning the query
  * will match the hit whether not the operator is applied).
  */
case class MarkedOp(op: TokenQueryOp, required: Boolean)

