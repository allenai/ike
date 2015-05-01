package org.allenai.dictionary.ml.compoundop

import org.allenai.dictionary.ml.queryop.{ TokenQueryOp, QueryOp }

import scala.collection.immutable.IntMap

/** Special op that makes no changes its query */
case class NullOp(numEdits: IntMap[Int]) extends CompoundQueryOp() {
  override def ops: Set[TokenQueryOp] = Set()
  override def canAdd(op: QueryOp): Boolean = false
  override def add(op: EvaluatedOp): CompoundQueryOp = throw new RuntimeException()
}
