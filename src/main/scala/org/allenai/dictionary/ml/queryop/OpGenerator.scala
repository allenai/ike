package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary._
import org.allenai.dictionary.ml._

import scala.collection.immutable.IntMap

object OpGenerator {

  def buildLeafMap(
    leafGenerator: QLeafGenerator,
    matches: Seq[QueryMatch]
  ): Map[QLeaf, IntMap[Int]] = {
    val operatorMap = scala.collection.mutable.Map[QLeaf, List[(Int, Int)]]()
    matches.view.zipWithIndex.foreach {
      case (queryMatch, index) =>
        val tokens = queryMatch.tokens
        val leaves = leafGenerator.generateLeaves(tokens)
        leaves.foreach { qLeaf =>
          val currentList = operatorMap.getOrElse(qLeaf, List[(Int, Int)]())
          operatorMap.put(qLeaf, (index, if (queryMatch.didMatch) 0 else 1) :: currentList)
        }
    }
    operatorMap.map { case (k, v) => k -> IntMap(v: _*) }.toMap
  }
}

/** Abstract class for classes that 'generate' possibles operation that could be applied to
  * to a query and calculates what sentences that operation would a starting query to match
  */
abstract class OpGenerator {
  def generate(matches: QueryMatches, examples: IndexedSeq[WeightedExample]): Map[QueryOp, IntMap[Int]]
}

