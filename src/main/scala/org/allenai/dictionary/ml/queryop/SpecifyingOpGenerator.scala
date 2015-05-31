package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary._
import org.allenai.dictionary.ml.{ WeightedExample, QueryMatches }

import scala.collection.immutable.IntMap

/** Builds QueryOps that makes a query more specific (match strictly less sentences)
  *
  * @param suggestPos whether to build operators that add QPos
  * @param suggestWord whether to build operators that add QWord
  */
case class SpecifyingOpGenerator(
    suggestPos: Boolean,
    suggestWord: Boolean,
    setRepeatedOp: Boolean = false,
    minSimilarityDifference: Int = 0
) extends OpGenerator {

  // Helper method the build a QLeafGenerator the gathers ops that would
  // specify the given input token
  private def getLeafGenerator(
    qexpr: QExpr,
    isCapture: Boolean
  ): QLeafGenerator = qexpr match {
    case QPos(_) => QLeafGenerator(pos = false, suggestWord && !isCapture)
    case QWildcard() => QLeafGenerator(suggestPos, suggestWord && !isCapture)
    case _ => QLeafGenerator(pos = false, word = false)
  }

  def generateForQSimilarPhrases(
      qsim: QSimilarPhrases,
      matches: QueryMatches,
      examples: IndexedSeq[WeightedExample],
      repeats: Option[(Int, Int)]
  ): Map[QueryOp, IntMap[Int]] = {
    val phraseMap = new SimilarPhraseMatchTracker(qsim)
    val slot = matches.queryToken.slot
    if (repeats.isEmpty) {
      matches.matches.view.zipWithIndex.foreach {
        case (queryMatch, index) =>
          val phrase = queryMatch.tokens.map(_.word)
          phraseMap.addPhrase(phrase, queryMatch.didMatch, index)
      }
    } else {
      val (min, max) = repeats.get
      matches.matches.view.zipWithIndex.foreach {
        case (queryMatch, index) =>
          val phrase = queryMatch.tokens.map(_.word).toIndexedSeq
          phraseMap.addPhrases(phrase, queryMatch.didMatch, index, min, max)
      }
    }
    val thresholds2Edit = phraseMap.generateOps(0, qsim.pos, minSimilarityDifference, examples)
    thresholds2Edit.map {
      case (threshold, edits) =>
        (SetToken(slot, qsim.copy(pos = threshold)), edits)
    }.toMap
  }

  def generateForNone(matches: QueryMatches): Map[QueryOp, IntMap[Int]] = {
    OpGenerator.getSetTokenOps(matches, QLeafGenerator(suggestPos, suggestWord))
  }

  def generateForQLeaf(qleafOpt: QLeaf, matches: QueryMatches): Map[QueryOp, IntMap[Int]] = {
    val isCapture = matches.queryToken.isCapture
    OpGenerator.getSetTokenOps(matches, getLeafGenerator(qleafOpt, isCapture))
  }

  def generateForQRepeating(
      repeatingOp: QRepeating,
      matches: QueryMatches,
      examples: IndexedSeq[WeightedExample]
  ): Map[QueryOp, IntMap[Int]] = {
    val index = matches.queryToken.slot.token
    val isCapture = matches.queryToken.isCapture

    // Get ops the involve changing the child token
    val leafGenerator = getLeafGenerator(repeatingOp.qexpr, isCapture)
    val setChildOps = repeatingOp.qexpr match {
      case qsp: QSimilarPhrases =>
        generateForQSimilarPhrases(
          qsp, matches, examples, Some((repeatingOp.min, repeatingOp.max))
        )
      case _ => OpGenerator.getSetTokenOps(matches, leafGenerator)
    }

    // Get ops that involve removing the token
    val (childMin, childMax) = QueryLanguage.getQueryLength(repeatingOp.qexpr)
    val removeOps =
      if (repeatingOp.min == 0) {
        val zeroMatches = matches.matches.view.zipWithIndex.filter {
          case (queryMatch, index) => queryMatch.tokens.isEmpty
        }.map {
          case (queryMatch, index) =>
            (index, if (queryMatch.didMatch) 1 else 0)
        }
        val zeroMatchesEditMap = IntMap(zeroMatches: _*)
        if (zeroMatchesEditMap.nonEmpty) {
          Seq((
            RemoveToken(index),
            zeroMatchesEditMap
          ))
        } else {
          Seq()
        }
      } else {
        Seq()
      }

    if (childMin != childMax) {
      // In this case it is harder to reason about the # of repetitions the query needs to match
      // each token sequence, so currently we stop here
      setChildOps ++ removeOps
    } else {
      // Gets op that involves changing the number of repetitions and tokens within a repetition
      case class Repetitions(index: Int, repeats: Int, required: Int)
      val editsWithSize = matches.matches.zipWithIndex.map {
        case (qMatch, matchIndex) =>
          Repetitions(matchIndex, qMatch.tokens.size / childMax,
            if (qMatch.didMatch) 0 else 1)
      }
      val nRepeats = editsWithSize.map(_.repeats).distinct
      val setMinOps = nRepeats.filter(_ != repeatingOp.min).map { n =>
        (SetMin(index, n), IntMap(editsWithSize.filter(_.repeats >= n).
          map(x => (x.index, x.required)): _*))
      }.filter(_._1.min <= repeatingOp.max)
      val setMaxOps = nRepeats.filter(_ != repeatingOp.max).map { n =>
        (SetMax(index, n), IntMap(editsWithSize.filter(_.repeats <= n).
          map(x => (x.index, x.required)): _*))
      }.filter(_._1.max >= repeatingOp.min)
      val repeatedOps = if (setRepeatedOp) {
        OpGenerator.getRepeatedOpMatch(matches, leafGenerator)
      } else {
        Seq()
      }
      (setMinOps ++ setMaxOps ++ removeOps ++ setChildOps ++ repeatedOps).toMap
    }
  }

  override def generate(matches: QueryMatches, examples: IndexedSeq[WeightedExample]):
  Map[QueryOp, IntMap[Int]] = {
    matches.queryToken.qexpr match {
      case None => generateForNone(matches)
      case Some(qs: QSimilarPhrases) => generateForQSimilarPhrases(qs, matches, examples, None)
      case Some(ql: QLeaf) => generateForQLeaf(ql, matches)
      case Some(qr: QRepeating) => generateForQRepeating(qr, matches, examples)
      case _ => Map()
    }
  }
}
