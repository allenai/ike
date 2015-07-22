package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.Span
import nl.inl.blacklab.search.lucene.{ BLSpans, HitQueryContext }

import java.util

/** Modifies `clause` so that it only matches Spans after the given document and token
  *
  * @param clause Spans to modify
  * @param startDoc document to start from, returns hits have doc >= startFromDoc
  * @param startToken token to start from, returned hits have doc > startFromDoc or
  *                      start >= startFromToken
  */
class SpansStartAt(clause: BLSpans, startDoc: Int, startToken: Int) extends BLSpans {

  var initialized = false

  def initialize(): Boolean = {
    if (!clause.skipTo(startDoc)) {
      false
    } else {
      while (clause.start() < startToken && clause.doc == startDoc) {
        if (!clause.next()) return false
      }
      true
    }
  }

  override def next(): Boolean = {
    if (!initialized) {
      initialized = true
      initialize()
    } else {
      clause.next()
    }
  }

  override def skipTo(target: Int): Boolean = {
    if (!initialized) {
      initialized = true
      if (target > doc) {
        clause.skipTo(target)
      } else {
        initialize()
      }
    } else {
      clause.skipTo(target)
    }
  }

  override def passHitQueryContextToClauses(hitQueryContext: HitQueryContext) = {
    clause.setHitQueryContext(hitQueryContext)
  }

  override def getCapturedGroups(spans: Array[Span]): Unit = clause.getCapturedGroups(spans)

  override def doc(): Int = clause.doc

  override def start(): Int = clause.start

  override def end(): Int = clause.end

  override def hitsHaveUniqueStart(): Boolean = clause.hitsHaveUniqueStart

  override def hitsHaveUniqueEnd(): Boolean = clause.hitsHaveUniqueEnd

  override def hitsAllSameLength(): Boolean = clause.hitsAllSameLength

  override def hitsLength(): Int = clause.hitsLength

  override def hitsAreUnique(): Boolean = clause.hitsAreUnique

  override def hitsEndPointSorted(): Boolean = clause.hitsEndPointSorted

  override def getPayload: util.Collection[Array[Byte]] = clause.getPayload

  override def isPayloadAvailable: Boolean = clause.isPayloadAvailable

}
