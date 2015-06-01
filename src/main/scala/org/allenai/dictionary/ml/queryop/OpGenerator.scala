package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary._
import org.allenai.dictionary.ml._

import scala.collection.immutable.IntMap

object OpGenerator {

  /** Calculates which QLeaf could be used to match with QueryMatches. Note
    * 1: QueryMatches with not token are never matched to a QLeaf
    * 2: QueryMatches with multiple tokens will be matched to a QLeaf, if the QLeaf could
    * match the token if it was repeated a sufficient number of times
    *
    * @param leafGenerator QLeafGenerator to determines what leaves to build the map for
    * @param matches Sequence of QueryMatches to build the match for
    * @return
    */
  def buildLeafMap(
    leafGenerator: QLeafGenerator,
    matches: Seq[QueryMatch]
  ): Map[QLeaf, IntMap[Int]] = {
    // Mutable for update speed since this is performance-relevant code
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

