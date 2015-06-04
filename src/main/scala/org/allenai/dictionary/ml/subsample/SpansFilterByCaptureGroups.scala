package org.allenai.dictionary.ml.subsample

import java.util

import nl.inl.blacklab.search.Span
import nl.inl.blacklab.search.lucene.{ HitQueryContext, BLSpans }

/** Filters hits from a BLSpans object that do not contains capture groups that would also be
  * returned by another BLSpans object. Note the capture groups from the filter query will
  * not be returned
  *
  * @param query the 'query' spans to filter
  * @param filter the 'filter' spans to filter the query spans with
  * @param captureGroupNames the names of the captures groups to filter with, both the query and
  *              filter spans should contain these capture groups
  * @param startFromDoc document to start collecting hits from
  * @param startFromToken token to start collecting hits from
  */
class SpansFilterByCaptureGroups(
    query: BLSpans,
    filter: BLSpans,
    captureGroupNames: Seq[String],
    startFromDoc: Int = 0,
    startFromToken: Int = 0
) extends BLSpans {

  var more = true
  var initialized = false

  // Indices the capture and filter query use to store capture groups from captureGroupNames
  var captureIndicesToAnd = Seq[(Int, Int)]()

  // array to use when getting capture spans for the query spans
  var querySpanHolder: Array[Span] = null

  // array to use when getting capture spans for the filter spans
  var filterSpanHolder: Array[Span] = null

  def initialize(): Boolean = {
    initialized = true
    if (query.skipTo(startFromDoc) && filter.skipTo(startFromDoc)) {
      var continue = true
      // Move past startFromToken
      while (continue && query.start() < startFromToken && query.doc == startFromDoc) {
        continue = query.next()
      }
      continue && syncMatch()
    } else {
      false
    }
  }

  override def next(): Boolean = {
    if (!more) {
      false
    } else {
      more = if (!initialized) {
        initialize()
      } else {
        query.next() && syncMatch()
      }
      more
    }
  }

  override def skipTo(target: Int): Boolean = {
    if (!more) {
      false
    } else if (!initialize()) {
      initialized = true
      more = if (target > startFromDoc) {
        filter.skipTo(target) && query.skipTo(target)
      } else {
        initialize()
      }
      more = more && syncMatch()
      more
    } else {
      more = if (!query.skipTo(target)) {
        false
      } else if (filter.doc < target && !filter.skipTo(target)) {
        false
      } else {
        syncMatch()
      }
      more
    }
  }

  /* @return Whether this is are currently on a valid match, assuming both filter and query
   *     are on the same document
   */
  private def isValidMatch: Boolean = {
    filterSpanHolder.indices.foreach(filterSpanHolder.update(_, null))
    querySpanHolder.indices.foreach(querySpanHolder.update(_, null))
    filter.getCapturedGroups(filterSpanHolder)
    query.getCapturedGroups(querySpanHolder)
    captureIndicesToAnd.forall {
      case (leftIndex, rightIndex) =>
        // Span does not implement equals so we compare directly
        querySpanHolder(leftIndex).start == filterSpanHolder(rightIndex).start &&
          querySpanHolder(leftIndex).end == filterSpanHolder(rightIndex).end
    }
  }

  /* Makes we sure are on a valid matching, advancing if needed.
   *
   * @return false if no valid match could be found
   */
  private def syncMatch(): Boolean = {
    if (syncDoc()) {
      var continue = true
      var foundMatch = false
      while (continue) {
        continue =
          if (query.end < filter.start) {
            query.next() && syncDoc()
          } else if (filter.end < query.start) {
            filter.next() && syncDoc()
          } else if (isValidMatch) {
            foundMatch = true
            false
          } else {
            query.next() && syncDoc()
          }
      }
      foundMatch
    } else {
      false
    }
  }

  /* Makes sure query and filter are on the same document
   *
   * @return false if query and filter could not be set to be on the same document
   */
  private def syncDoc(): Boolean = {
    if (query.doc == filter.doc) {
      true
    } else {
      var foundMatch = false
      var continue = true
      while (continue) {
        if (query.doc < filter.doc) {
          continue = query.skipTo(filter.doc)
        }
        if (query.doc > filter.doc) {
          continue = filter.skipTo(query.doc)
        } else {
          // Left must be at least as large as Right after the first if statement, so if
          // Right is at least as large as Left after this statement we are done
          continue = false
          foundMatch = true
        }
      }
      foundMatch
    }
  }

  override def setHitQueryContext(context: HitQueryContext): Unit = {
    super.setHitQueryContext(context) // Will call passHitQueryContextToClauses
    val filterContext = new HitQueryContext()
    filter.setHitQueryContext(filterContext)

    val queryIndicesToAnd = captureGroupNames.map(context.getCapturedGroupNames.indexOf(_))
    val filterIndicesToAnd = captureGroupNames.map(filterContext.getCapturedGroupNames.indexOf(_))
    captureIndicesToAnd = queryIndicesToAnd.zip(filterIndicesToAnd)
    require(captureIndicesToAnd.forall { case (a, b) => a >= 0 && b >= 0 })
    filterSpanHolder = Array.fill[Span](filterContext.numberOfCapturedGroups())(null)
    querySpanHolder = Array.fill[Span](context.numberOfCapturedGroups())(null)
  }

  override def passHitQueryContextToClauses(context: HitQueryContext): Unit = {
    query.setHitQueryContext(context)
  }

  override def getCapturedGroups(capturedGroups: Array[Span]): Unit = {
    // Any valid hit will have already set querySpanHolder, not point in recomputing
    System.arraycopy(querySpanHolder, 0, capturedGroups, 0, querySpanHolder.length)
  }

  override def doc(): Int = query.doc

  override def start(): Int = query.start

  override def end(): Int = query.end

  override def hitsHaveUniqueStart(): Boolean = query.hitsHaveUniqueStart

  override def hitsHaveUniqueEnd(): Boolean = query.hitsHaveUniqueEnd

  override def hitsAllSameLength(): Boolean = query.hitsAllSameLength

  override def hitsLength(): Int = query.hitsLength

  override def hitsAreUnique(): Boolean = query.hitsAreUnique

  override def hitsEndPointSorted(): Boolean = query.hitsEndPointSorted

  override def getPayload: util.Collection[Array[Byte]] = query.getPayload

  override def isPayloadAvailable: Boolean = query.isPayloadAvailable
}
