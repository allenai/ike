package org.allenai.dictionary.ml.compoundops

import org.allenai.dictionary.ml.primitveops.TokenQueryOp

import scala.collection.immutable.IntMap

object EvaluatedOp {
  def fromList(op: TokenQueryOp, matches: Seq[Int]): EvaluatedOp = {
    EvaluatedOp(op, IntMap(matches.map((_, 0)): _*))
  }

  def fromPairs(op: TokenQueryOp, matches: Seq[(Int, Int)]): EvaluatedOp = {
    EvaluatedOp(op, IntMap(matches: _*))
  }
}

/** TokenQueryOp that is paired with a cache of what sentences
  * inside the Hits object the op was created from this operator matches
  *
  * @param op operatorw
  * @param matches Map of (sentence index) -> 1, if this operator fills a requirement
  *               for the sentence and 0 if this operator matches the sentence
  *
  */
case class EvaluatedOp(op: TokenQueryOp, matches: IntMap[Int])
