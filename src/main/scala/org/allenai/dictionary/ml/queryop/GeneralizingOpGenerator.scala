package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary._
import org.allenai.dictionary.ml.{ QueryToken, QueryMatches }

import scala.collection.immutable.IntMap

/** Builds QueryOps that make a query more general, so it will match more sentences. The
  * generalization is approximate, so some sentences that were matched originally might no longer
  * match once ops produces by this are applied
  *
  * @param suggestPos whether to build operators that add POS in the query
  * @param suggestWord whether to build operators that add words to the query
  * @param addToken whether to suggest AddToken ops in addition to SetToken ops
  * @param maxRemoves the maximum number of tokens that can be removed, this will stop this from
  *             suggesting RemoveEdge ops that would require removing more then that many
  *             tokens
  */
case class GeneralizingOpGenerator(
    suggestPos: Boolean,
    suggestWord: Boolean,
    addToken: Boolean,
    queryLength: Int,
    generalizationPruning: Boolean = true,
    maxRemoves: Int = Int.MaxValue
) extends OpGenerator {

  /* Builds a QLeafGenerator to determine what QLeaf to use in SetTokenOps for the
   * the given query expression
   */
  private def getSetTokenLeaves(qexpr: QExpr, isCapture: Boolean): QLeafGenerator = {
    qexpr match {
      case QWord(_) => QLeafGenerator(suggestPos, word = false)
      case QPos(_) => QLeafGenerator(pos = false, word = false)
      case _ => QLeafGenerator(pos = false, word = false)
    }
  }

  /* Builds a QLeafGenerator to determine what QLeaf to use in AddTokenOps for the
   * the given query expression
   */
  private def getAddTokenLeaves(qexpr: QExpr, isCapture: Boolean): QLeafGenerator = {
    qexpr match {
      case q: QWord => QLeafGenerator(pos = false, !isCapture, Set(q))
      case q: QPos => QLeafGenerator(suggestWord, word = false, Set(q))
      case QDisj(qexprs) =>
        val avoid = qexprs.flatMap {
          case q: QLeaf => Some(q)
          case _ => None
        }.toSet
        val anyPos = qexprs.forall(!_.isInstanceOf[QPos])
        val anyWord = qexprs.forall(!_.isInstanceOf[QWord])
        QLeafGenerator(
          (!anyPos || anyWord) && suggestPos,
          (anyPos || !anyWord) && suggestWord, avoid
        )
      case _ => QLeafGenerator(pos = false, word = false)
    }
  }

  override def generate(matches: QueryMatches): Map[QueryOp, IntMap[Int]] = {
    val slot = matches.queryToken.slot
    require(slot.isInstanceOf[QueryToken] && matches.queryToken.qexpr.isDefined)
    val qexpr = matches.queryToken.qexpr
    require(matches.queryToken.slot.isInstanceOf[QueryToken])
    val setTokenLeaves = getSetTokenLeaves(qexpr.get, matches.queryToken.isCapture)
    val setTokenOps = OpGenerator.getSetTokenOps(matches, setTokenLeaves)

    // Prune out ops that were never suggested when the QExpr did match, as these ops could not
    // by said to 'generalize' QExpr (for example, this prevents us suggesting 'DT' as a
    // replacement for the starting QExpr QWord(cat))
    val prunedSetTokenOps = if (generalizationPruning) {
      setTokenOps.filter { case (_, hitMap) => !hitMap.values.forall(_ == 1) }
    } else {
      setTokenOps
    }

    val removeOps = if (!matches.queryToken.isCapture && (
      matches.queryToken.firstTokenSequence || matches.queryToken.lastTokenSequence
    )) {
      val distance = if (matches.queryToken.firstTokenSequence) {
        slot.token
      } else {
        queryLength - slot.token
      }
      if (distance <= maxRemoves) {
        val removeOp =
          if (matches.queryToken.firstTokenSequence) {
            if (slot.token == 1) {
              RemoveToken(slot.token)
            } else {
              RemoveEdge(slot.token, 1)
            }
          } else {
            if (slot.token == queryLength) {
              RemoveToken(slot.token)
            } else {
              RemoveEdge(slot.token, queryLength)
            }
          }
        val removable = IntMap(matches.matches.zipWithIndex.map {
          case (queryMatch, index) => (index, if (queryMatch.didMatch) 0 else 1)
        }: _*)
        Map[QueryOp, IntMap[Int]](removeOp -> removable)
      } else {
        Map[QueryOp, IntMap[Int]]()
      }
    } else {
      Map[QueryOp, IntMap[Int]]()
    }

    val addTokenOps =
      if (addToken) {
        val addTokenLeaves = getAddTokenLeaves(qexpr.get, matches.queryToken.isCapture)
        OpGenerator.getAddTokenOps(matches, addTokenLeaves)
      } else {
        Map[QueryOp, IntMap[Int]]()
      }
    prunedSetTokenOps ++ addTokenOps ++ removeOps
  }
}
