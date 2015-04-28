package org.allenai.dictionary.ml.compoundop

import org.allenai.dictionary.ml.Slot
import org.allenai.dictionary.ml.queryop._
import org.allenai.dictionary.ml.queryop.TokenCombination._
import scala.collection.immutable.IntMap

object OpConjunctionOfDisjunctions {
  def apply(
    evaluatedOp: EvaluatedOp,
    maxRemoves: Int = Int.MaxValue
  ): Option[OpConjunctionOfDisjunctions] = evaluatedOp.op match {
    case tq: TokenQueryOp => Some(new OpConjunctionOfDisjunctions(Set(tq), evaluatedOp.matches,
      Map(tq.slot -> evaluatedOp.matches), maxRemoves))
    case _ => None // Cannot initialize with a non-TokenQueryOp
  }
}

/** CompoundOp that combines ops that are compatible by either AND or OR operations. This comes
  * at the price of some additional computation expense relative to OpConjunction
  *
  * @param ops TokeQueryOps that this contains
  * @param numEdits maps sentence indices the number of required edits this has made to that
  *           sentence
  * @param perSlotEdits Map slots -> maps of sentences indices number of edits made to that sentence
  *               by operations that were applied to that slot.
  * @param maxRemoves Maximum number of RemoveToken(1) operations that can be added to this
  */
case class OpConjunctionOfDisjunctions private (
    override val ops: Set[TokenQueryOp],
    override val numEdits: IntMap[Int],
    perSlotEdits: Map[Slot, IntMap[Int]],
    maxRemoves: Int
) extends CompoundQueryOp(ops, numEdits) {

  override def canAdd(op: QueryOp): Boolean = op match {
    case re: RemoveEdge =>
      re.afterRemovals.forall(ops contains RemoveToken(_)) &&
        canAdd(RemoveToken(re.index))
    case rt: RemoveToken => !(perSlotEdits contains rt.slot) && maxRemoves > 0
    case tq: TokenQueryOp =>
      if (perSlotEdits contains tq.slot) {
        val otherOps = ops.filter(_.slot == tq.slot)
        val combinations = otherOps.map(x => x.combinable(tq))
        combinations.forall(_ == AND) || combinations.forall(_ == OR)
      } else {
        true
      }
  }

  override def add(op: EvaluatedOp): OpConjunctionOfDisjunctions = {
    require(canAdd(op.op))
    val newOp = op.op match {
      case tq: TokenQueryOp => tq
      case RemoveEdge(index, _) => RemoveToken(index)
    }

    val (newPerSlot, recalculate) =
      if (perSlotEdits contains newOp.slot) {
        val otherOps = ops.filter(_.slot == newOp.slot)
        if (newOp.combinable(otherOps.head) == OR) {
          val newSlot = perSlotEdits(newOp.slot).unionWith(
            op.matches, (_, v1: Int, v2: Int) => math.min(1, v1 + v2)
          )
          (perSlotEdits + (newOp.slot -> newSlot), true)
        } else {
          val newSlot = perSlotEdits(newOp.slot).intersectionWith(
            op.matches, (_, v1: Int, v2: Int) => math.min(1, v1 + v2)
          )
          (perSlotEdits + (newOp.slot -> newSlot), false)
        }
      } else {
        (perSlotEdits + (newOp.slot -> op.matches), false)
      }

    val newMatches = if (recalculate) {
      // A previously existing slot's IntMap was changed, so we can't just AND numEdits
      // and Matches, we have to to recompute it from each per-slot IntMap
      newPerSlot.values.reduce((a, b) =>
        a.intersectionWith(b, (_, v1: Int, v2: Int) => v1 + v2))
    } else {
      numEdits.intersectionWith(op.matches, (_, v1: Int, v2: Int) => v1 + v2)
    }
    val newMaxRemove = maxRemoves - (if (op.op.isInstanceOf[RemoveToken]) 1 else 0)
    new OpConjunctionOfDisjunctions(ops + newOp, newMatches, newPerSlot, newMaxRemove)
  }
}
