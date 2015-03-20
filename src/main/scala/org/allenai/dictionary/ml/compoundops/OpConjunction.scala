package org.allenai.dictionary.ml.compoundops

import org.allenai.dictionary.ml.primitveops._

import scala.collection.immutable.IntMap

object OpConjunction {
  def apply(op: EvaluatedOp): OpConjunction =
    new OpConjunction(Set(op.op), op.matches)
}

/** Class that combines operations by applying a single operation for each token in
  * for a given query
  */
case class OpConjunction private (
  override val ops: Set[TokenQueryOp],
  override val numEdits: IntMap[Int]
)
    extends CompoundQueryOp(ops, numEdits) {

  def this(size: Int) = this(
    Set(),
    IntMap[Int](Range(0, size).map((_, 0)): _*)
  )

  override def canAdd(op: TokenQueryOp): Boolean = {
    !ops.exists(_.slot == op.slot)
  }

  override def add(op: EvaluatedOp): OpConjunction = {
    require(canAdd(op.op))
    val newNumEdits = numEdits.intersectionWith(op.matches, (_, v1: Int, v2: Int) => v1 + v2)
    new OpConjunction(ops + op.op, newNumEdits)
  }

  override def hashCode(): Int = ops.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case c: OpConjunction => ops.equals(c.ops)
    case _ => false
  }
}
