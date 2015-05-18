package org.allenai.dictionary.ml.compoundop

import org.allenai.dictionary.ml.queryop._
import org.allenai.dictionary.ml.queryop.TokenCombination._
import scala.collection.immutable.IntMap

object OpConjunction {
  def apply(op: EvaluatedOp, maxRemoves: Int = Int.MaxValue):
  Option[OpConjunction] = op.op match {
    case tq: TokenQueryOp => Some(new OpConjunction(Set(tq), op.matches, maxRemoves))
    case _ => None
  }
}

/** Class that combines operations that can be combined by ANDing them together
  */
case class OpConjunction private (
    ops: Set[TokenQueryOp],
    numEdits: IntMap[Int],
    maxRemoves: Int
) extends CompoundQueryOp() {

  override def canAdd(op: QueryOp): Boolean = op match {
    case rt: RemoveToken => !ops.exists(x => x.slot == rt.slot) && maxRemoves > 0
    case re: RemoveEdge => re.afterRemovals.forall(ops contains RemoveToken(_)) &&
      canAdd(RemoveToken(re.index))
    case tq: TokenQueryOp => !ops.exists(x => x.slot == tq.slot && x.combinable(tq) != AND)
  }

  override def add(op: QueryOp, matches: IntMap[Int]): OpConjunction = {
    require(canAdd(op))
    val addEdits = op match {
      case tq: TokenQueryOp => !ops.exists(_.slot == tq.slot)
      case _ => true
    }
    val newNumEdits =
      if (addEdits) {
        numEdits.intersectionWith(matches, (_, v1: Int, v2: Int) => v1 + v2)
      } else {
        numEdits.intersectionWith(matches, (_, v1: Int, v2: Int) => math.min(1, v1 + v2))
      }
    val newOp = op match {
      case RemoveEdge(index, _) => RemoveToken(index)
      case tq: TokenQueryOp => tq
    }
    val newMaxRemove = maxRemoves - (if (op.isInstanceOf[RemoveToken]) 1 else 0)
    new OpConjunction(ops + newOp, newNumEdits, newMaxRemove)
  }
}
