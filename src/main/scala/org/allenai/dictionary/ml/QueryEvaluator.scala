package org.allenai.dictionary.ml

import org.allenai.dictionary.ml.compoundops.CompoundQueryOp

import scala.collection.immutable.IntMap

import Label._

/** Trait for classes the return a scores for the results of a query the reflects how
  * good that query will be as a stand alone pattern.
  */
abstract class QueryEvaluator(examples: IndexedSeq[Example]) {

  // Cache these counts for subclasses to use
  val positiveSize: Double = examples count (_.label == Positive)
  val negativeSize: Double = examples count (_.label == Negative)
  val unlabelledSize: Double = examples count (_.label == Unknown)

  /** @return Whether this evaluator makes use of unlabelled examples
    *       to compute its scoring function
    */
  def usesUnlabelledData(): Boolean

  /** @return Whether this evaluator accounts for the current search depth when computing
    *       its scoring function.
    */
  def usesDepth(): Boolean

  /** Returns a score determining the general 'goodness' of a query operation
    *
    * @param ops The operation to score
    * @param depth Depth of the current search
    * @return score of the query
    */
  def evaluate(ops: CompoundQueryOp, depth: Int): Double

  /** @return Returns a message describing how they score for the given operation was
    *       arrived at. This message might be displayed on the frontend to users.
    */
  def evaluationMsg(ops: CompoundQueryOp, depth: Int): String = {
    evaluate(ops, depth).toString
  }

  /** @return As evaluationMsg, but may return a more detailed message. This message
    *       will only be used for debugging
    */
  def evaluationMsgLong(ops: CompoundQueryOp, depth: Int): String = {
    evaluate(ops, depth).toString
  }

  /** Counts the number of positive, negative, and unlabelled examples a mapping
    * from (sentence_id -> # of edits made to that sentence) would match
    */
  protected def countOccurances(map: IntMap[Int]): (Int, Int, Int) = {
    var positive = 0
    var negative = 0
    var unlabelled = 0
    map.foreach {
      case (key, numEdits) =>
        val example = examples(key)
        if (numEdits >= example.requiredEdits) {
          if (example.label == Positive) {
            positive += 1
          } else if (example.label == Negative) {
            negative += 1
          } else {
            unlabelled += 1
          }
        }
    }
    (positive, negative, unlabelled)
  }

}

case class PositiveMinusNegative(
  examples: IndexedSeq[Example],
  negativeWeight: Double
)
    extends QueryEvaluator(examples) {

  val usesDepth = false
  val usesUnlabelledData = false

  override def evaluate(ops: CompoundQueryOp, depth: Int = 0): Double = {
    val (positive, negative, _) = countOccurances(ops.numEdits)
    positive - negative * negativeWeight
  }
}

case class PRCoverageSum(
  examples: IndexedSeq[Example],
  positiveWeight: Double,
  negativeWeight: Double,
  unlabelledWeight: Double
)
    extends QueryEvaluator(examples) {

  val usesDepth = false
  val usesUnlabelledData = true

  protected def getCounts(matches: IntMap[Int]): (Double, Double, Double) = {

    def safeDivide(num: Int, denom: Double): Double = {
      if (denom == 0) 0 else num / denom
    }

    val (positiveHits, negativeHits, unlabelledHits) = countOccurances(matches)

    (
      safeDivide(positiveHits, positiveSize),
      safeDivide(negativeHits, negativeSize),
      safeDivide(unlabelledHits, unlabelledSize)
    )
  }

  override def evaluate(ops: CompoundQueryOp, depth: Int): Double = {
    val matches = ops.numEdits
    val (p, n, u) = getCounts(matches)
    if (p == 0) {
      0
    } else {
      p * positiveWeight + n * negativeWeight + u * unlabelledWeight
    }
  }

  override def evaluationMsg(ops: CompoundQueryOp, depth: Int): String = {
    val matches = ops.numEdits
    val (positiveHits, negativeHits, unlabelledHits) = countOccurances(matches)
    "P: %d/%1.0f, N: %d/%1.0f, U: %d/%1.0f".format(
      positiveHits, positiveSize,
      negativeHits, negativeSize,
      unlabelledHits, unlabelledSize
    )
  }

  override def evaluationMsgLong(ops: CompoundQueryOp, depth: Int): String = {
    val matches = ops.numEdits
    val (p, n, u) = getCounts(matches)
    evaluationMsg(ops, depth) + ("\n%.3f: p: (%.3f * %.3f = %.3f)" +
      " n: (%.3f * %.3f = %.3f) u: (%.3f * %.3f = %.3f)").format(
        evaluate(ops, depth), p, positiveWeight, p * positiveWeight,
        n, negativeWeight, n * negativeWeight,
        u, unlabelledWeight, u * unlabelledWeight
      )
  }
}

case class PRCoverageSumPartial(
  examples: IndexedSeq[Example],
  positiveWeight: Double,
  negativeWeight: Double,
  unlabelledWeight: Double,
  maxDepth: Int
)
    extends QueryEvaluator(examples) {

  val usesDepth = true
  val usesUnlabelledData = true

  def safeDivide(num: Double, denom: Double): Double = {
    if (denom == 0) 0 else num / denom.toDouble
  }

  def getWeightedScore(numLeft: Int, editsDone: IntMap[Int]): (Double, Double, Double) = {
    var totalPositiveScore = 0.0
    var totalNegativeScore = 0.0
    var totalUnknownScore = 0.0
    editsDone foreach {
      case (exNum, numEditsDone) =>
        val example = examples(exNum)
        val requiredEdits = example.requiredEdits
        val editsStillNeeded = requiredEdits - numEditsDone
        if (editsStillNeeded <= numLeft) {
          val weight = 1 - math.max(editsStillNeeded, 0) / maxDepth
          if (example.label == Positive) {
            totalPositiveScore += weight
          } else if (example.label == Negative) {
            totalNegativeScore += weight
          } else {
            totalUnknownScore += weight
          }
        }
    }
    (
      safeDivide(totalPositiveScore, positiveSize),
      safeDivide(totalNegativeScore, negativeSize),
      safeDivide(totalUnknownScore, unlabelledSize)
    )
  }

  override def evaluate(ops: CompoundQueryOp, depth: Int): Double = {
    val matches = ops.numEdits
    val (p, n, u) = getWeightedScore(maxDepth - depth, matches)
    if (p == 0) {
      0
    } else {
      p * positiveWeight + n * negativeWeight + u * unlabelledWeight
    }
  }

  override def evaluationMsg(ops: CompoundQueryOp, depth: Int): String = {
    val matches = ops.numEdits
    val (positiveHits, negativeHits, unlabelledHits) = countOccurances(matches)
    "P: %d/%1.0f, N: %d/%1.0f, U: %d/%1.0f".format(
      positiveHits, positiveSize,
      negativeHits, negativeSize,
      unlabelledHits, unlabelledSize
    )
  }

  override def evaluationMsgLong(ops: CompoundQueryOp, depth: Int): String = {
    val matches = ops.numEdits
    val (p, n, u) = getWeightedScore(maxDepth - depth, matches)
    evaluationMsg(ops, depth) + ("\n%.3f: p: (%.3f * %.3f = %.3f)" +
      " n: (%.3f * %.3f = %.3f) u: (%.3f * %.3f = %.3f)").format(
        evaluate(ops, depth), p, positiveWeight, p * positiveWeight,
        n, negativeWeight, n * negativeWeight,
        u, unlabelledWeight, u * unlabelledWeight
      )
  }
}
