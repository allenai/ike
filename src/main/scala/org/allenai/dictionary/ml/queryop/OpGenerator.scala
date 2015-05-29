package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary._
import org.allenai.dictionary.ml._

import scala.collection.immutable.IntMap

object OpGenerator {

  private def buildLeafMap(
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


  def getRepeatedOpMatch(
    matches: QueryMatches,
    leafGenerator: QLeafGenerator
  ): Map[SetRepeatedToken, IntMap[Int]] = {
    val operatorMap = scala.collection.mutable.Map[SetRepeatedToken, List[(Int, Int)]]()
    matches.matches.view.zipWithIndex.foreach {
      case (queryMatch, matchIndex) =>
        val tokens = queryMatch.tokens
        tokens.view.zipWithIndex.foreach {
          case (token, tokenIndex) =>
            leafGenerator.generateLeaves(token).foreach { qLeaf =>
              val op = SetRepeatedToken(matches.queryToken.slot, tokenIndex + 1, qLeaf)
              val currentList = operatorMap.getOrElse(op, List[(Int, Int)]())
              operatorMap.put(op, (matchIndex, if (queryMatch.didMatch) 0 else 1) :: currentList)
            }
        }
    }
    operatorMap.map { case (k, v) => k -> IntMap(v: _*) }.toMap
  }

  def getSetTokenOps(
    matches: QueryMatches,
    leafGenerator: QLeafGenerator
  ): Map[QueryOp, IntMap[Int]] = {
    if (!leafGenerator.pos && !leafGenerator.word) {
      Map()
    } else {
      OpGenerator.buildLeafMap(leafGenerator, matches.matches).map {
        case (k, v) => SetToken(matches.queryToken.slot, k) -> v
      }
    }
  }

  def getAddTokenOps(
    queryMatches: QueryMatches,
    leafGenerator: QLeafGenerator
  ): Map[QueryOp, IntMap[Int]] = {
    require(queryMatches.queryToken.slot.isInstanceOf[QueryToken])
    // AddToken ops implicitly match everything that is currently matched, add that back in
    val allReadyMatches = IntMap(queryMatches.matches.view.zipWithIndex.flatMap {
      case (qMatch, index) => if (qMatch.didMatch) Some((index, 0)) else None
    }: _*)
    OpGenerator.buildLeafMap(leafGenerator, queryMatches.matches).filter {
      // Filter ops that match every single existing match, in that case we prefer using SetTokenOp
      case (leaf, matches) => allReadyMatches.intersection(matches).size != allReadyMatches.size
    }.map {
      case (k, v) =>
        AddToken(queryMatches.queryToken.slot.token, k) ->
          v.unionWith(allReadyMatches, (_, v1, v2) => v1 + v2)
    }
  }
}

/** Abstract class for classes that 'generate' possibles operation that could be applied to
  * to a query and calculates what sentences that operation would a starting query to match
  */
abstract class OpGenerator {
  def generate(matches: QueryMatches, examples: IndexedSeq[WeightedExample]):
  Map[QueryOp, IntMap[Int]]
}

