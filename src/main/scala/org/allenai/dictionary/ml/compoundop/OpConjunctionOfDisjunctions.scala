package org.allenai.dictionary.ml.compoundop

import org.allenai.dictionary.ml.Slot
import org.allenai.dictionary.ml.queryop._
import org.allenai.dictionary.ml.queryop.TokenCombination._
import scala.collection.immutable.IntMap

object OpConjunctionOfDisjunctions {
  def apply(
    op: EvaluatedOp,
    maxRemoves: Int = Int.MaxValue
  ): Option[OpConjunctionOfDisjunctions] = op.op match {
    case tq: TokenQueryOp => Some(new OpConjunctionOfDisjunctions(Set(tq), op.matches,
      Map(tq.slot -> op.matches), maxRemoves))
    case _ => None // Cannot initialize with a non-TokenQueryOp
  }
}

/** CompoundOp that combines ops that are compatible by either AND or OR operations. This comes
  * at the price of some additional computation expense relative to OpConjunction
  *
  * @param ops TokeQueryOps that this contains
  * @param numEdits maps sentence indices the number of required edits this has made to that
  *        sentence
  * @param perSlotEdits Map slots -> maps of sentences indices number of edits made to that sentence
  *            by operations that were applied to that slot.
  * @param maxRemoves Maximum number of RemoveToken(1) operations that can be added to this
  */
case class OpConjunctionOfDisjunctions private (
    ops: Set[TokenQueryOp],
    numEdits: IntMap[Int],
    perSlotEdits: Map[Slot, IntMap[Int]],
    maxRemoves: Int
) extends CompoundQueryOp() {

  override def canAdd(op: QueryOp): Boolean = op match {
    case re: RemoveEdge =>
      re.afterRemovals.forall(ops contains RemoveToken(_)) &&
        canAdd(RemoveToken(re.index))
    case rt: RemoveToken => !(perSlotEdits contains rt.slot) && maxRemoves > 0
    case tq: TokenQueryOp =>
      if (perSlotEdits contains tq.slot) {
        val otherOps = ops.filter(_.slot == tq.slot)
        val combinations = otherOps.map(x => x.combinable(tq))
        // We do not allow OR if this is the only slot because it is usually preferable to
        // suggest two separate queries in this case
        combinations.forall(_ == AND) ||
          (combinations.forall(_ == OR) && perSlotEdits.size != 1)
      } else {
        true
      }
  }

  override def add(op: QueryOp, matches: IntMap[Int]): OpConjunctionOfDisjunctions = {
    require(canAdd(op))
    val newOp = op match {
      case tq: TokenQueryOp => tq
      case RemoveEdge(index, _) => RemoveToken(index)
    }

    val (newPerSlot, recalculate) =
      if (perSlotEdits contains newOp.slot) {
        val otherOps = ops.filter(_.slot == newOp.slot)
        if (newOp.combinable(otherOps.head) == OR) {
          val newSlot = perSlotEdits(newOp.slot).unionWith(
            matches, (_, v1: Int, v2: Int) => math.min(1, v1 + v2)
          )
          (perSlotEdits + (newOp.slot -> newSlot), true)
        } else {
          val newSlot = perSlotEdits(newOp.slot).intersectionWith(
            matches, (_, v1: Int, v2: Int) => math.min(1, v1 + v2)
          )
          (perSlotEdits + (newOp.slot -> newSlot), false)
        }
      } else {
        (perSlotEdits + (newOp.slot -> matches), false)
      }

    val newMatches = if (recalculate) {
      // A previously existing slot's IntMap was changed, so we can't just AND numEdits
      // and Matches, we have to to recompute it from each per-slot IntMap
      newPerSlot.values.reduce((a, b) =>
        a.intersectionWith(b, (_, v1: Int, v2: Int) => v1 + v2))
    } else {
      numEdits.intersectionWith(matches, (_, v1: Int, v2: Int) => v1 + v2)
    }
    val newMaxRemove = maxRemoves - (if (op.isInstanceOf[RemoveToken]) 1 else 0)
    new OpConjunctionOfDisjunctions(ops + newOp, newMatches, newPerSlot, newMaxRemove)
  }
}
