package org.allenai.ike.ml.compoundop

import org.allenai.ike.ml.queryop.QueryOp

import scala.collection.immutable.IntMap

object EvaluatedOp {
  def fromList(op: QueryOp, matches: Seq[Int]): EvaluatedOp = {
    EvaluatedOp(op, IntMap(matches.map((_, 0)): _*))
  }

  def fromPairs(op: QueryOp, matches: Seq[(Int, Int)]): EvaluatedOp = {
    EvaluatedOp(op, IntMap(matches: _*))
  }
}

/** QueryOp that is paired with a cache of what sentences inside the Hits object the op was
  * created from this operator matches
  * @param op operator
  * @param matches Map of (sentence index) -> 1, if this operator fills a requirement for the
  * sentence and -> 0 if the associated query this operator was built from would match the sentence
  * once this operator is applied
  */
case class EvaluatedOp(op: QueryOp, matches: IntMap[Int])
