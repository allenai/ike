package org.allenai.dictionary.ml

import org.allenai.dictionary.ml.Label._
import org.allenai.dictionary.ml.compoundop.CompoundQueryOp

import scala.collection.immutable.IntMap

object QueryEvaluator {

  /** Counts the number of positive, negative, and unlabelled examples a mapping
    * from (sentence_id -> # of edits made to that sentence) would match
    *
    * @param sentenceId2EditCount map of sentence indices to number of edits done to that sentence
    * @param examples list of examples
    */
  def countOccurrences(
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

  /** Counts the number of positive, negative, and unlabelled examples a mapping
    * from (sentence_id -> # of edits made to that sentence) would match,
    * giving partial credit to sentences that are close to being matched
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
  def usesDepth(): Boolean

  /** Returns a score determining the general 'goodness' of a query operation
    *
    * @param op The operation to score, op.numEdits should specify the number of edits that
    *   operation will make to each sentence in this.examples
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

  override def evaluate(op: CompoundQueryOp, depth: Int) = {
    val (p, n, u) = getSubScores(op, depth)
    (p * positiveWeight + n * negativeWeight + u * unlabelledWeight)
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

/** Weighted sum of positive, negative, and unlabelled examples */
case class SumEvaluator(
    examples: IndexedSeq[WeightedExample],
    positiveWeight: Double,
    negativeWeight: Double,
    unlabelledWeight: Double
) extends PerLabelEvaluator(examples, positiveWeight, negativeWeight, unlabelledWeight) {

  override def getSubScores(op: CompoundQueryOp, depth: Int): (Double, Double, Double) = {
    QueryEvaluator.countOccurrences(op.numEdits, examples)
  }

  override def usesDepth(): Boolean = false
}

class PositivePlusNegative(
    examples: IndexedSeq[WeightedExample],
    negativeWeight: Double
) extends SumEvaluator(examples, 1, negativeWeight, 0) {

  override def usesDepth(): Boolean = false
}

/** Weighted sum of positive, negative, and unlabelled examples with credit to partially
  * completed examples
  */
case class PartialSumEvaluator(
    examples: IndexedSeq[WeightedExample],
    positiveWeight: Double,
    negativeWeight: Double,
    unlabelledWeight: Double,
    maxDepth: Int
) extends PerLabelEvaluator(examples, positiveWeight, negativeWeight, unlabelledWeight) {

  override def getSubScores(op: CompoundQueryOp, depth: Int): (Double, Double, Double) = {
    QueryEvaluator.countPartialOccurrences(op.numEdits, examples, maxDepth, maxDepth - depth)
  }

  override def usesDepth(): Boolean = true
}

/** Weighted sum of positive, negative, and unlabelled examples normalized by the number of
  * possible positive, negative, and unlabelled examples
  */
case class NormalizedSumEvaluator(
    examples: IndexedSeq[WeightedExample],
    positiveWeight: Double,
    negativeWeight: Double,
    unlabelledWeight: Double
) extends PerLabelEvaluator(examples, positiveWeight, negativeWeight, unlabelledWeight) {

  val totalPositiveWeight: Double = examples.map(x => if (x.label == Positive) x.weight else 0).sum
  val totalNegativeWeight: Double = examples.map(x => if (x.label == Negative) x.weight else 0).sum
  val totalUnlabelledWeight: Double = examples.map(x =>
    if (x.label == Unlabelled) x.weight else 0).sum

  override def getSubScores(op: CompoundQueryOp, depth: Int): (Double, Double, Double) = {
    val (p, n, u) = QueryEvaluator.countOccurrences(op.numEdits, examples)
    def safeDivide(num: Double, denom: Double): Double = {
      if (denom == 0) 0 else num / denom.toDouble
    }
    (
      safeDivide(p, totalPositiveWeight),
      safeDivide(n, totalNegativeWeight),
      safeDivide(u, totalUnlabelledWeight)
    )
  }

  override def usesDepth(): Boolean = false
}