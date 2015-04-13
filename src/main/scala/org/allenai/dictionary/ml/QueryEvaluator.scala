package org.allenai.dictionary.ml

import org.allenai.dictionary.ml.Label._
import org.allenai.dictionary.ml.compoundop.CompoundQueryOp

import scala.collection.immutable.IntMap

/** Base class for classes that, given a query operation, returns a score that reflects how
  * good that query would be as a suggestion to the user
  *
  * @param examples The examples to evaluate query operation with
  */
abstract class QueryEvaluator(val examples: IndexedSeq[WeightedExample]) {

  // Cache these counts for subclasses to use
  val positiveSize: Double = examples count (_.label == Positive)
  val negativeSize: Double = examples count (_.label == Negative)
  val unlabelledSize: Double = examples count (_.label == Unlabelled)

  val totalPositiveWeight: Double = examples.map(x => if (x.label == Positive) x.weight else 0).sum
  val totalNegativeWeight: Double = examples.map(x => if (x.label == Negative) x.weight else 0).sum
  val totalUnlabelledWeight: Double = examples.map(x =>
    if (x.label == Unlabelled) x.weight else 0).sum

  /** @return Whether this evaluator accounts for the current search depth when computing
    *   its scoring function
    */
  def usesDepth(): Boolean

  /** Returns a score determining the general 'goodness' of a query operation
    *
    * @param op The operation to score, op.numEdits should specify the number of edits that
    *       operation will make to each sentence in this.examples
    * @param depth Depth of the current search
    * @return score of the query
    */
  def evaluate(op: CompoundQueryOp, depth: Int): Double

  /** @return Returns a message describing how they score for the given operation was
    *   arrived at. This message might be displayed on the frontend to users.
    */
  def evaluationMsg(op: CompoundQueryOp, depth: Int): String = {
    evaluate(op, depth).toString
  }

  /** @return As evaluationMsg, but may return a more detailed message. This message
    *   will only be used for debugging
    */
  def evaluationMsgLong(op: CompoundQueryOp, depth: Int): String = {
    evaluate(op, depth).toString
  }

  /** Counts the number of positive, negative, and unlabelled examples a mapping
    * from (sentence_id -> # of edits made to that sentence) would match
    */
  protected def countOccurrences(
    sentenceId2EditCount: IntMap[Int]
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
}

case class PositiveMinusNegative(
  override val examples: IndexedSeq[WeightedExample],
  negativeWeight: Double
)
    extends QueryEvaluator(examples) {

  override val usesDepth = false

  override def evaluate(op: CompoundQueryOp, depth: Int = 0): Double = {
    val (positive, negative, _) = countOccurrences(op.numEdits)
    positive - negative * negativeWeight
  }
}

/** Scores queries using a weighted sum of coverage on positive examples, coverage on negative
  * examples, coverage on unlabelled examples
  */
case class CoverageSum(
  override val examples: IndexedSeq[WeightedExample],
  positiveWeight: Double,
  negativeWeight: Double,
  unlabelledWeight: Double
)
    extends QueryEvaluator(examples) {

  val usesDepth = false
  val usesUnlabelledData = true

  protected def getCounts(matches: IntMap[Int]): (Double, Double, Double) = {

    def safeDivide(num: Double, denom: Double): Double = {
      if (denom == 0) 0 else num / denom
    }

    val (positiveHits, negativeHits, unlabelledHits) = countOccurrences(matches)

    (
      safeDivide(positiveHits, totalPositiveWeight),
      safeDivide(negativeHits, totalNegativeWeight),
      safeDivide(unlabelledHits, totalUnlabelledWeight)
    )
  }

  override def evaluate(op: CompoundQueryOp, depth: Int): Double = {
    val matches = op.numEdits
    val (p, n, u) = getCounts(matches)
    if (p == 0) {
      Double.NegativeInfinity
    } else {
      // Tiny size penalty, most servers as a tie-breaker
      val sizePenalty = (100 - op.size) / 100.0
      (p * positiveWeight + n * negativeWeight + u * unlabelledWeight) * sizePenalty
    }
  }

  override def evaluationMsg(op: CompoundQueryOp, depth: Int): String = {
    val matches = op.numEdits
    val (positiveHits, negativeHits, unlabelledHits) = countOccurrences(matches)
    "P: %.1f/%1.0f, N: %.1f/%1.0f, U: %.1f/%1.0f".format(
      positiveHits, positiveSize,
      negativeHits, negativeSize,
      unlabelledHits, unlabelledSize
    )
  }

  override def evaluationMsgLong(op: CompoundQueryOp, depth: Int): String = {
    val matches = op.numEdits
    val (p, n, u) = getCounts(matches)
    "\n%.3f: p: (%.3f * %.3f = %.3f) n: (%.3f * %.3f = %.3f) u: (%.3f * %.3f = %.3f)".format(
      evaluate(op, depth), p, positiveWeight, p * positiveWeight,
      n, negativeWeight, n * negativeWeight,
      u, unlabelledWeight, u * unlabelledWeight
    )
  }
}

/** Scores queries using a weighted sum of coverage on positive examples, coverage on negative
  * examples, and coverage on unlabelled examples. Additionally down weights sentences based on
  * how many more edits are needed for the query to match the input sentence, compared with the
  * number of edits that could still potentially be added to the query
  */
case class WeightedCoverageSum(
  override val examples: IndexedSeq[WeightedExample],
  positiveWeight: Double,
  negativeWeight: Double,
  unlabelledWeight: Double,
  maxDepth: Int
)
    extends QueryEvaluator(examples) {

  override val usesDepth = true

  def getWeightedScore(
    numberOfPossibleFutureEdits: Int,
    editsDone: IntMap[Int]
  ): (Double, Double, Double) = {

    def safeDivide(num: Double, denom: Double): Double = {
      if (denom == 0) 0 else num / denom.toDouble
    }

    var totalPositiveScore = 0.0
    var totalNegativeScore = 0.0
    var totalUnlabelledScore = 0.0
    editsDone foreach {
      case (exNum, numEditsDone) =>
        val example = examples(exNum)
        val requiredEdits = example.requiredEdits
        val editsStillNeeded = requiredEdits - numEditsDone
        if (editsStillNeeded <= numberOfPossibleFutureEdits) {
          val weight = example.weight * (1 - math.max(editsStillNeeded, 0) / maxDepth)
          example.label match {
            case Positive => totalPositiveScore += weight
            case Negative => totalNegativeScore += weight
            case Unlabelled => totalUnlabelledScore += weight
          }
        }
    }
    (
      safeDivide(totalPositiveScore, totalPositiveWeight),
      safeDivide(totalNegativeScore, totalNegativeWeight),
      safeDivide(totalUnlabelledScore, totalUnlabelledWeight)
    )
  }

  override def evaluate(op: CompoundQueryOp, depth: Int): Double = {
    val matches = op.numEdits
    val (p, n, u) = getWeightedScore(maxDepth - depth, matches)
    if (p == 0) {
      Double.NegativeInfinity
    } else {
      p * positiveWeight + n * negativeWeight + u * unlabelledWeight
    }
  }

  override def evaluationMsg(op: CompoundQueryOp, depth: Int): String = {
    val matches = op.numEdits
    val (positiveHits, negativeHits, unlabelledHits) = getWeightedScore(maxDepth - depth, matches)
    "P: %.1f/%1.0f, N: %.1f/%1.0f, U: %.1f/%1.0f".format(
      positiveHits, totalPositiveWeight,
      negativeHits, totalNegativeWeight,
      unlabelledHits, totalUnlabelledWeight
    )
  }

  override def evaluationMsgLong(op: CompoundQueryOp, depth: Int): String = {
    val matches = op.numEdits
    val (p, n, u) = getWeightedScore(maxDepth - depth, matches)
    "\n%.3f: p: (%.3f * %.3f = %.3f) n: (%.3f * %.3f = %.3f) u: (%.3f * %.3f = %.3f)".format(
      evaluate(op, depth), p, positiveWeight, p * positiveWeight,
      n, negativeWeight, n * negativeWeight, u, unlabelledWeight, u * unlabelledWeight
    )
  }
}
