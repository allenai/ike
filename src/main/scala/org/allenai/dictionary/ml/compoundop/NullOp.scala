package org.allenai.dictionary.ml.compoundop

import org.allenai.dictionary.ml.queryop.QueryOp

import scala.collection.immutable.IntMap

/** Special op that makes no changes its query */
case class NullOp(
    override val numEdits: IntMap[Int]
) extends CompoundQueryOp(Set(), numEdits) {
  override def canAdd(op: QueryOp): Boolean = false
  override def add(op: EvaluatedOp): CompoundQueryOp = throw new RuntimeException()
}
