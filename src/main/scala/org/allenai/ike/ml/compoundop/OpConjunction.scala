package org.allenai.ike.ml.compoundop

import org.allenai.ike.ml.queryop.TokenCombination._
import org.allenai.ike.ml.queryop._

import scala.collection.immutable.IntMap

object OpConjunction {
  def apply(op: EvaluatedOp): Option[OpConjunction] = op.op match {
    case tq: TokenQueryOp => Some(new OpConjunction(Set(tq), op.matches))
    case _ => None
  }
}

/** Class that combines operations that can be combined by ANDing them together
  */
// Note that this OpConjunction is currently not used in favour of OpConjunctionOfDisjunction
// with carefully restricted Disjunction slots.
case class OpConjunction private (
    ops: Set[TokenQueryOp],
    numEdits: IntMap[Int]
) extends CompoundQueryOp() {

  override def canAdd(op: QueryOp): Boolean = op match {
    case rt: RemoveToken => !ops.exists(x => x.slot == rt.slot)
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
      case tq: TokenQueryOp => tq
    }
    new OpConjunction(ops + newOp, newNumEdits)
  }
}
