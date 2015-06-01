package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary.{ SimilarPhrase, QSimilarPhrases }
import org.allenai.dictionary.ml.Label._
import org.allenai.dictionary.ml.WeightedExample

import scala.collection.immutable.IntMap

/** Given a QSimilarPhrases, this class tracks which sentences that QSimilarPhrases could match
  * if the POS threshold of that QSimilarPhrases was increased
  */
class SimilarPhraseMatchTracker(val qSimilarPhrases: QSimilarPhrases) {

  // List of (sentenceIndex, minPOS needed to matched that sentence)
  private var hits: List[(Int, Int, Int)] = List()

  // Maps phrases -> minPos needed for qSimilarPhrases to match that phrase
  private val phraseToRank = {
    val allPhrases = SimilarPhrase(qSimilarPhrases.qwords, 0) +: qSimilarPhrases.phrases
    allPhrases.sortBy(_.similarity).reverse.zipWithIndex.map {
      case (phrases, index) => phrases.qwords.map(_.value) -> (index + 1)
    }.toMap + (qSimilarPhrases.qwords.map(_.value) -> 0)
  }

  lazy val maxPhraseLength = qSimilarPhrases.phrases.map(_.qwords.size).max

  def addPhrase(phrase: Seq[String], didMatch: Boolean, index: Int): Unit = {
    if (phraseToRank.contains(phrase)) {
      val posNeeded = phraseToRank(phrase)
      hits = (index, if (didMatch) 0 else 1, posNeeded) :: hits
    }
  }

  def minSimForPhrases(phrases: IndexedSeq[String], min: Int, max: Int): Int = {
    if (phrases.isEmpty) {
      0
    } else if (phrases.size == 1) {
      phraseToRank.getOrElse(phrases, -1)
    } else {
      // Solve a mini dynamic programming problem to decide the min setting needed to match the
      // token sequence.

      val adjustedMax = if (max == -1) {
        phrases.size
      } else {
        Math.min(max, phrases.size)
      }

      // Stores (word matched up to, #phrases used) -> minPosSetting needed
      val minRank = Array.fill[Array[Int]](phrases.size + 1)(Array.fill[Int](adjustedMax + 1)(-1))

      // Initialize (rank 0, word 0) -> 0 phrases needed
      minRank(0)(0) = 0

      // Precompute (word matched up to) -> min and max #phrases we could use to reach that word
      // and still match the whole sequence using an allowable number of phrases
      val wordToPhrasesUsedLimits = (0 until phrases.size).map { word =>
        val wordsLeft = phrases.size - word
        val maxPhrasesNeeded = word
        val minPhrasesNeeded = word / maxPhraseLength
        val minRankPossible = Math.max(
          min - wordsLeft,
          minPhrasesNeeded
        )
        val maxRankPossible = Math.min(
          adjustedMax - (wordsLeft + maxPhraseLength - 1) / maxPhraseLength,
          maxPhrasesNeeded
        )
        (minRankPossible, maxRankPossible)
      }

      var stuck = false
      var forWord = 1
      while (!stuck && forWord < phrases.size + 1) {
        var anyFound = false
        (Math.max(0, forWord - maxPhraseLength) until forWord).foreach { fromWord =>
          val slice = phrases.slice(fromWord, forWord)
          if (phraseToRank contains slice) {
            val sim = phraseToRank(slice)
            val (minPhrasesUsed, maxPhrasesUsed) = wordToPhrasesUsedLimits(fromWord)
            (minPhrasesUsed to maxPhrasesUsed).foreach { fromRank =>
              if (minRank(fromWord)(fromRank) != -1) {
                minRank(forWord)(fromRank + 1) = Math.max(minRank(fromWord)(fromRank), sim)
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
      val filtered = minRank.last.filter(_ != -1)
      if (filtered.nonEmpty) {
        filtered.min
      } else {
        -1
      }
    }
  }

  def addPhrases(phrases: IndexedSeq[String], didMatch: Boolean, index: Int, min: Int,
    max: Int): Unit = {
    val minDistance = minSimForPhrases(phrases, min, max)
    if (minDistance > -1) {
      hits = (index, if (didMatch) 0 else 1, minDistance) :: hits
    }
  }

  /** Gets a sequence of (pos threshold, edit map) that correspond to pos thresholds that can be
    * used to qSimilarPhrases
    *
    * @param minCoverageDifference no two thresholds returned will cover sets of sentences that only
    *                              differ by this much
    * @param examples Examples the sentence indices correspond to
    * @return List of (POS threshold, edit map) pairs
    */
  def generateOps(minPosThreshold: Int, maxPosThreshold: Int, minCoverageDifference: Int,
    examples: IndexedSeq[WeightedExample]): List[(Int, IntMap[Int])] = {
    // Iterate through the minPosThreshold in sorted order and build a suggestion Op
    // where the 1: the threshold changes and label of the relevant examples changes,
    // and the the op would not violate minCoverageDifference
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