package org.allenai.dictionary.ml.compoundops

import org.allenai.dictionary.ml.primitveops.{ Slot, TokenQueryOp }

import scala.collection.immutable.IntMap

/** CompoundOp that combines all SetToken and AddToken TokenQueryOps that apply to the same slot
  * within a query as a disjunction
  */
object OpConjunctionOfDisjunctions {
  def apply(evaluatedOp: EvaluatedOp): OpConjunctionOfDisjunctions =
    new OpConjunctionOfDisjunctions(Set(evaluatedOp.op), evaluatedOp.matches,
      Map(evaluatedOp.op.slot -> evaluatedOp.matches))
}

case class OpConjunctionOfDisjunctions private (
    override val ops: Set[TokenQueryOp],
    override val numEdits: IntMap[Int],
    perSlotEdits: Map[Slot, IntMap[Int]]
) extends CompoundQueryOp(ops, numEdits) {

  override def canAdd(op: TokenQueryOp): Boolean = true

  override def add(op: EvaluatedOp): OpConjunctionOfDisjunctions = {
    val (newPerSlot, newMatches) =
      if (perSlotEdits contains op.op.slot) {
        // Add this op to an existing slot, so OR it in with the rest of the edits per that
        // slot and then recalculate the total edit counts
        val newSlotOps = perSlotEdits(op.op.slot).unionWith(
          op.matches,
          (_, v1: Int, v2: Int) => math.min(1, v1 + v2)
        )
        val newPerSlot = perSlotEdits + (op.op.slot -> newSlotOps)
        val matches = newPerSlot.values.reduce((a, b) =>
          a.intersectionWith(b, (_, v1: Int, v2: Int) => v1 + v2))
        (newPerSlot, matches)
      } else {
        // New slot, so we can just AND it in with the current edit counts
        (perSlotEdits + (op.op.slot -> op.matches),
          numEdits.intersectionWith(op.matches, (_, v1: Int, v2: Int) => v1 + v2))
      }
    new OpConjunctionOfDisjunctions(ops + op.op, newMatches, newPerSlot)
  }

  override def hashCode(): Int = ops.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case c: OpConjunctionOfDisjunctions => ops.equals(c.ops)
    case _ => false
  }
}
