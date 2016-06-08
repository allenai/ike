package org.allenai.ike.ml.subsample

import org.allenai.blacklab.search.Span
import org.allenai.blacklab.search.lucene.{ BLSpans, HitQueryContext }

import java.util

/** Returns spans that have a minimum number of valid capture groups, where a valid capture
  * group is one that is non-null and whose end is non-negative
  *
  * @param clause Spans to filter
  * @param requiredMatches Number of required matches
  * @param capturesToCheck Names of the capture groups to check, should be registered by clause
  */
class SpansMinimumValidCaptures(
    clause: BLSpans,
    requiredMatches: Int,
    capturesToCheck: Seq[String]
) extends BLSpans {

  var more = true
  var captureGroupHolder: Array[Span] = Array()
  var captureIndicesToCheck: Seq[Int] = Seq()
  var clauseNumCaptureGroups = -1
  var clauseFirstCaptureGroupIndex = -1

  override def passHitQueryContextToClauses(context: HitQueryContext): Unit = {
    clauseFirstCaptureGroupIndex = context.getCaptureRegisterNumber
    clause.setHitQueryContext(context)
    captureGroupHolder = Array.fill[Span](context.numberOfCapturedGroups())(null)
    clauseNumCaptureGroups = context.getCaptureRegisterNumber - clauseFirstCaptureGroupIndex
    val captureGroupsNames = context.getCapturedGroupNames
    require(capturesToCheck.forall(captureGroupsNames.contains))
    captureIndicesToCheck = capturesToCheck.map(captureGroupsNames.indexOf)
  }

  override def getCapturedGroups(capturedGroups: Array[Span]): Unit = {
    // Any valid hit will have already filled captureGroupHolder
    System.arraycopy(captureGroupHolder, clauseFirstCaptureGroupIndex,
      capturedGroups, clauseFirstCaptureGroupIndex, clauseNumCaptureGroups)
  }

  def onValidMatch: Boolean = {
    captureGroupHolder.indices.foreach(captureGroupHolder.update(_, null))
    clause.getCapturedGroups(captureGroupHolder)
    val numValidCaptures = captureIndicesToCheck.count(i => {
      val span = captureGroupHolder(i)
      span != null && span.end > 0
    })
    numValidCaptures >= requiredMatches
  }

  override def skipTo(target: Int): Boolean = {
    if (more) {
      more = clause.skipTo(target)
      while (more && !onValidMatch) {
        more = clause.next()
      }
    }
    more
  }

  override def next(): Boolean = {
    if (more) {
      more = clause.next()
      while (more && !onValidMatch) {
        more = clause.next()
      }
    }
    more
  }

  override def doc(): Int = clause.doc

  override def start(): Int = clause.start

  override def end(): Int = clause.end

  override def hitsHaveUniqueStart(): Boolean = clause.hitsHaveUniqueStart()

  override def hitsHaveUniqueEnd(): Boolean = clause.hitsHaveUniqueEnd()

  override def hitsAllSameLength(): Boolean = clause.hitsAllSameLength()

  override def hitsLength(): Int = clause.hitsLength()

  override def hitsAreUnique(): Boolean = clause.hitsAreUnique()

  override def hitsEndPointSorted(): Boolean = clause.hitsEndPointSorted()

  override def getPayload: util.Collection[Array[Byte]] = clause.getPayload

  override def isPayloadAvailable: Boolean = clause.isPayloadAvailable
}
