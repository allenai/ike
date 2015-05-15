package org.allenai.dictionary.ml.subsample

import java.util

import nl.inl.blacklab.search.Span
import nl.inl.blacklab.search.lucene.{HitQueryContext, BLSpans}

class SpansValidateCaptures(
    clause: BLSpans,
    requiredMatches: Int,
    capturesToCheck: Seq[String]
) extends BLSpans {

  var more = true
  var captureGroupHolder: Array[Span] = Array()
  var captureIndicesToCheck: Seq[Int] = Seq()
  var numCaptures = -1
  var captureStart = -1

  override def passHitQueryContextToClauses(context: HitQueryContext): Unit = {
    captureStart = context.getCaptureRegisterNumber
    clause.setHitQueryContext(context)
    captureGroupHolder = Array.fill[Span](context.numberOfCapturedGroups())(null)
    numCaptures = context.getCaptureRegisterNumber - captureStart
    captureIndicesToCheck = capturesToCheck.map(context.registerCapturedGroup(_))
  }

  override def getCapturedGroups(capturedGroups: Array[Span]): Unit = {
    System.arraycopy(captureGroupHolder, captureStart,
      capturedGroups, captureStart, numCaptures)
  }

  def validate(): Boolean = {
    clause.getCapturedGroups(captureGroupHolder)
    captureIndicesToCheck.count(i => {
      val span = captureGroupHolder(i)
      span != null && span.end > 0
    }) >= requiredMatches
  }

  override def skipTo(target: Int): Boolean = {
    if (more) {
      more = clause.skipTo(target)
      while (more && !validate()) {
        more = clause.next()
      }
    }
    more
  }

  override def next(): Boolean = {
    if (more) {
      more = clause.next()
      while(more && !validate()) {
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

  override def getSpan: Span = clause.getSpan

  override def hitsEndPointSorted(): Boolean = clause.hitsEndPointSorted()

  override def getPayload: util.Collection[Array[Byte]] = clause.getPayload

  override def isPayloadAvailable: Boolean = clause.isPayloadAvailable
}
