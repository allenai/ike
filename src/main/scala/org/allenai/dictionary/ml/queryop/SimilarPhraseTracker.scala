package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary.QSimilarPhrases
import org.allenai.dictionary.ml.Label._
import org.allenai.dictionary.ml.WeightedExample

import scala.collection.immutable.IntMap

/** Given a QSimilarPhrases, this class tracks which sentences that QSimilarPhrases could match
  * if the POS threshold of that QSimilarPhrases was increased
  */
class SimilarPhraseMatchTracker(val qSimilarPhrases: QSimilarPhrases) {

  // List of (sentenceIndex, minPOS needed to matched that sentence)
  private var hits: List[(Int, Int, Int)] = List()

  // Maps phrases -> minPos needed for qSimilarPhrases to match that rank
  private val similarities =
    qSimilarPhrases.phrases.sortBy(_.similarity).reverse.zipWithIndex.map {
      case (phrases, index) => phrases.qwords.map(_.value) -> (index + 1)
    }.toMap + (qSimilarPhrases.qwords.map(_.value) -> 0)

  lazy val maxPhraseLength = qSimilarPhrases.phrases.map(_.qwords.size).max

  def addPhrase(phrase: Seq[String], didMatch: Boolean, index: Int): Unit = {
    if (similarities.contains(phrase)) {
      val posNeeded = similarities(phrase)
      hits = (index, if (didMatch) 0 else 1, posNeeded) :: hits
    }
  }

  def addPhrases(phrases: IndexedSeq[String], didMatch: Boolean, index: Int, min: Int,
    max: Int): Unit = {

    val adjustedMax = if (max == -1) {
      phrases.size
    } else {
      Math.min(max, phrases.size)
    }

    // Solve a mini dynamic programming problem to decide the min setting needed
    // Store (#words match, #matches used) -> minPosSetting needed to match up to that word using
    // that number of sequences
    val minRank = Array.fill[Array[Int]](phrases.size + 1)(Array.fill[Int](adjustedMax)(-1))
    minRank(0) = Array.fill[Int](adjustedMax)(0)
    var stuck = false
    var forWord = 1
    while (!stuck && forWord < phrases.size) {
      var anyFound = false
      (Math.max(0, forWord - maxPhraseLength) until forWord).foreach { fromWord =>
        val slice = phrases.slice(fromWord, forWord)
        if (similarities contains slice) {
          val sim = similarities(slice)
          val wordsLeft = phrases.size - forWord
          val minRankPossible = Math.max(1, min - wordsLeft)
          val maxRankPossible = adjustedMax - wordsLeft
          (minRankPossible to maxRankPossible).foreach { fromRank =>
            if (minRank(fromWord)(fromRank - 1) != -1) {
              minRank(forWord)(fromRank) = Math.max(minRank(fromWord)(fromRank - 1), sim)
              anyFound = true
            }
          }
        }
      }
      if (!anyFound) {
        stuck = true
      }
      forWord += 1
    }
    val mindistance = minRank.last.filter(_ != -1).min
    if (mindistance > -1) {
      hits = (index, if (didMatch) 0 else 1, mindistance) :: hits
    }
  }

  /** Gets a sequence of (pos threshold, edit map) that correspond to pos thresholds that can be used
    * to qSimilarPhrases
    *
    * @param minCoverageDifference no two thresholds returned will cover sets of sentences that only
    *                              differ by this much
    * @param examples Examples the sentence indices correspond to
    * @return List of (POS threshold, edit map) pairs
    */
  def generateOps(minPosThreshold: Int, maxPosThreshold: Int, minCoverageDifference: Int,
    examples: IndexedSeq[WeightedExample]): List[(Int, IntMap[Int])] = {
    val sortedHits = hits.filter(_._3 <= maxPosThreshold).sortBy(_._3)
    var curThreshold = sortedHits.head._3
    var curLabel: Option[Label] = Some(examples(sortedHits.head._1).label)
    var prevLabel: Option[Label] = None
    var curMatches: List[(Int, Int)] = List((sortedHits.head._1, sortedHits.head._2))
    var prevSize = sortedHits.count(_._3 == 0)
    var size = 1
    var ops = List[(Int, IntMap[Int])]()
    sortedHits.drop(1).foreach {
      case (sentenceIndex, editsMade, posThreshold) =>
        val label = examples(sentenceIndex).label
        if (posThreshold != curThreshold && curThreshold >= minPosThreshold &&
          (size - prevSize) >= minCoverageDifference) {
          if (curLabel.isDefined && (curLabel == prevLabel)) {
            // The previous block added nothing but the same as label this block label, drop it
            ops = ops.drop(1)
          }
          val editMap = IntMap(curMatches: _*)
          ops = (curThreshold, editMap) :: ops
          prevLabel = curLabel
          curLabel = Some(label)
          prevSize = size
        }
        curLabel = if (curLabel.isDefined && curLabel.get != label) {
          None
        } else {
          curLabel
        }
        size += 1
        curThreshold = posThreshold
        curMatches = (sentenceIndex, editsMade) :: curMatches
    }
    // Add the last max POS op (checking for label continuity as usual)
    if ((size - prevSize) >= minCoverageDifference) {
      if (curLabel.isDefined && (curLabel == prevLabel)) {
        ops = ops.drop(1)
      }
      ops = (curThreshold, IntMap(curMatches: _*)) :: ops
    }
    ops
  }
}