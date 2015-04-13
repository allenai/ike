package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.lucene.{ BLSpansWrapper, SpanQueryBase }
import org.apache.lucene.index.{ TermContext, Term, AtomicReaderContext }
import org.apache.lucene.search.spans.{ Spans, SpanQuery }
import org.apache.lucene.util.Bits

import java.util

/** SpanQuery the filters hits from a query that do not capture and the same spans as another
  * query. Note the capture groups from the filter query will not be returned.
  *
  * @param _query The query to filter
  * @param _filter The query to filter by
  * @param captureGroups Capture groups to filter the query by, both the filter and the query should
  *                     contain capture groups with the names in this list
  */
class SpanQueryFilterByCaptureGroups(
    _query: SpanQuery,
    _filter: SpanQuery,
    captureGroups: Seq[String],
    startFromDoc: Int = 0
) extends SpanQueryBase(_query, _filter) {

  def query: SpanQuery = clauses(0)
  def filter: SpanQuery = clauses(1)

  override def getField: String = query.getField

  override def getSpans(context: AtomicReaderContext, acceptDocs: Bits,
    termContexts: util.Map[Term, TermContext]): Spans = {
    val leftSpans = query.getSpans(context, acceptDocs, termContexts)
    val filterSpans = filter.getSpans(context, acceptDocs, termContexts)
    return new SpansFilterByCaptureGroup(
      BLSpansWrapper.optWrap(leftSpans),
      BLSpansWrapper.optWrap(filterSpans),
      captureGroups,
      startFromDoc
    )
  }

  override def toString(field: String): String = {
    query.toString(field) + s" FILTERED BY <${filter.toString(field)}>"
  }
}
