package org.allenai.dictionary.ml

import org.allenai.dictionary.ml.Label._
import org.allenai.dictionary.ml.compoundop.CompoundQueryOp

import scala.collection.immutable.IntMap

object QueryEvaluator {

  /** Counts the total weight of positive, negative, and unlabelled examples a mapping
    * from (sentence_id -> # of edits made to that sentence) would match
    *
    * @param sentenceId2EditCount map of sentence indices to number of edits done to that sentence
    * @param examples list of examples
    */
  def countWeight(
    sentenceId2EditCount: IntMap[Int],
    examples: IndexedSeq[WeightedExample]
  ): (Double, Double, Double) = {
    var positive = 0.0
    var negative = 0.0
    var unlabelled = 0.0
    sentenceId2EditCount.foreach {
      case (key, numEdits) =>
        val example = examples(key)
        if (numEdits >= example.requiredEdits) {
          example.label match {
            case Positive => positive += example.weight
            case Negative => negative += example.weight
            case Unlabelled => unlabelled += example.weight
          }
        }
    }
    (positive, negative, unlabelled)
  }

  /** Counts the number of unique positive, negative, and unlabelled examples a mapping
    * from (sentence_id -> # of edits made to that sentence) would match
    *
    * @param sentenceId2EditCount map of sentence indices to number of edits done to that sentence
    * @param examples list of examples
    */
  def countOccurrences(
    sentenceId2EditCount: IntMap[Int],
    examples: IndexedSeq[WeightedExample]
  ): (Double, Double, Double) = {
    var positive = scala.collection.mutable.Set[Int]()
    var negative = scala.collection.mutable.Set[Int]()
    var unlabelled = scala.collection.mutable.Set[Int]()
    sentenceId2EditCount.foreach {
      case (key, numEdits) =>
        val example = examples(key)
        if (numEdits >= example.requiredEdits) {
          example.label match {
            case Positive => positive += example.phraseId
            case Negative => negative += example.phraseId
            case Unlabelled => unlabelled += example.phraseId
          }
        }
    }
    (positive.size, negative.size, unlabelled.size)
  }

  /** Counts the number of unique positive, negative, and unlabelled examples a mapping
    * from (sentence_id -> # of edits made to that sentence) would match, giving
    * partial credit to sentences that are close to being matched
    *
    * @param sentenceId2EditCount map of sentence indices to number of edits done to that sentence
    * @param examples list of examples
    * @param maxEdits maximum number of edits that can ever be done to any sentence
    * @param editsLeft number of edits that we might be able do to any sentence in the future
    */
  def countPartialOccurrences(
    sentenceId2EditCount: IntMap[Int],
    examples: IndexedSeq[WeightedExample],
    maxEdits: Int,
    editsLeft: Int
  ): (Double, Double, Double) = {
    var positive = scala.collection.mutable.Set[Int]()
    var negative = scala.collection.mutable.Set[Int]()
    var unlabelled = scala.collection.mutable.Set[Int]()
    sentenceId2EditCount.foreach {
      case (exNum, numEditsDone) =>
        val example = examples(exNum)
        val requiredEdits = example.requiredEdits
        val editsStillNeeded = requiredEdits - numEditsDone
        if (editsStillNeeded <= editsLeft) {
          example.label match {
            case Positive => positive += example.phraseId
            case Negative => negative += example.phraseId
            case Unlabelled => unlabelled += example.phraseId
          }
        }
    }
    (positive.size, negative.size, unlabelled.size)
  }

  /** Counts the total weight of positive, negative, and unlabelled examples a mapping
    * from (sentence_id -> # of edits made to that sentence) would match,
    * giving partial credit to sentences that are close to being matched
    *
    * @param sentenceId2EditCount map of sentence indices to number of edits done to that sentence
    * @param examples list of examples
    * @param maxEdits maximum number of edits that can ever be done to any sentence
    * @param editsLeft number of edits that we might be able do to any sentence in the future
    */
  def countPartialWeight(
    sentenceId2EditCount: IntMap[Int],
    examples: IndexedSeq[WeightedExample],
    maxEdits: Int,
    editsLeft: Int
  ): (Double, Double, Double) = {
    var positive = 0.0
    var negative = 0.0
    var unlabelled = 0.0
    sentenceId2EditCount.foreach {
      case (exNum, numEditsDone) =>
        val example = examples(exNum)
        val requiredEdits = example.requiredEdits
        val editsStillNeeded = requiredEdits - numEditsDone
        if (editsStillNeeded <= editsLeft) {
          val weight = example.weight * (1 - math.max(editsStillNeeded, 0) / maxEdits)
          example.label match {
            case Positive => positive += weight
            case Negative => negative += weight
            case Unlabelled => unlabelled += weight
          }
        }
    }
    (positive, negative, unlabelled)
  }
}

/** Base class for classes that, given a query operation, returns a score that reflects how
  * good that query would be as a suggestion to the user
  */
abstract class QueryEvaluator() {

  /** @return Whether this evaluator accounts for the current search depth when computing
    * its scoring function, if so scores are expected to be monotonically decreasing with depth
    */
  def usesDepth: Boolean

  /** Returns a score determining the general 'goodness' of a query operation
    *
    * @param op The operation to score, op.numEdits should specify the number of edits that
    * operation will make to each sentence in this.examples
    * @param depth Depth of the current search
    * @return score of the query
    */
  def evaluate(op: CompoundQueryOp, depth: Int): Double

  /** @return Returns a message describing how they score for the given operation was
    * arrived at, Strictly for debugging purposes
    */
  def evaluationMsg(op: CompoundQueryOp, depth: Int): String = {
    evaluate(op, depth).toString
  }
}

/** Classes that score queries by taking a weighted sum of a sub-scores for each possible label */
abstract class PerLabelEvaluator(
    examples: IndexedSeq[WeightedExample],
    positiveWeight: Double,
    negativeWeight: Double,
    unlabelledWeight: Double
) extends QueryEvaluator() {

  def getSubScores(op: CompoundQueryOp, depth: Int): (Double, Double, Double)

  override def evaluate(op: CompoundQueryOp, depth: Int): Double = {
    val (p, n, u) = getSubScores(op, depth)
    if (p == 0) {
      Double.NegativeInfinity
    } else {
      p * positiveWeight + n * negativeWeight + u * unlabelledWeight
    }
  }

  override def evaluationMsg(op: CompoundQueryOp, depth: Int): String = {
    val (p, n, u) = getSubScores(op, depth)
    "%.1f, <p: (%.1f * %.1f = %.1f) n: (%.1f * %.1f = %.1f) u: (%.1f * %.1f = %.1f)>"
      .format(
        evaluate(op, depth), p, positiveWeight, p * positiveWeight,
        n, negativeWeight, n * negativeWeight,
        u, unlabelledWeight, u * unlabelledWeight
      )
  }
}

/** Weighted sum of either 1) The number of unique phrases returned per each label or 2) The
  * weighted sum of all the examples matched per label, depending on `countUniquePhrases`
  */
case class SumEvaluator(
    examples: IndexedSeq[WeightedExample],
    positiveWeight: Double,
    negativeWeight: Double,
    unlabelledWeight: Double,
    countUniquePhrases: Boolean
) extends PerLabelEvaluator(examples, positiveWeight, negativeWeight, unlabelledWeight) {

  override def getSubScores(op: CompoundQueryOp, depth: Int): (Double, Double, Double) = {
    if (countUniquePhrases) {
      QueryEvaluator.countOccurrences(op.numEdits, examples)
    } else {
      QueryEvaluator.countWeight(op.numEdits, examples)
    }
  }

  override def usesDepth: Boolean = false
}

class PositivePlusNegative(
    examples: IndexedSeq[WeightedExample],
    negativeWeight: Double
) extends SumEvaluator(examples, 1, negativeWeight, 0, false) {

  override def usesDepth: Boolean = false
}

/** As SumEvaluator, but gives credit to partially matched examples
  */
case class PartialSumEvaluator(
    examples: IndexedSeq[WeightedExample],
    positiveWeight: Double,
    negativeWeight: Double,
    unlabelledWeight: Double,
    maxDepth: Int,
    useWeights: Boolean
) extends PerLabelEvaluator(examples, positiveWeight, negativeWeight, unlabelledWeight) {

  override def getSubScores(op: CompoundQueryOp, depth: Int): (Double, Double, Double) = {
    if (useWeights) {
      QueryEvaluator.countPartialWeight(op.numEdits, examples, maxDepth, maxDepth - depth)

    } else {
      QueryEvaluator.countPartialOccurrences(op.numEdits, examples, maxDepth, maxDepth - depth)
    }
  }

  override def usesDepth: Boolean = true
}
