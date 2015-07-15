package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.lucene.{ BLSpansWrapper, SpanQueryBase }
import org.apache.lucene.index.{ TermContext, Term, AtomicReaderContext }
import org.apache.lucene.search.spans.{ Spans, SpanQuery }
import org.apache.lucene.util.Bits

import java.util

/** SpanQuery the filters hits from a query that do not capture and the same spans as another
  * query. In other words a capture group level AND between two queries.
  *
  * @param _query The query to filter
  * @param _filter the filter to AND the query against
  * @param captureGroups Capture groups to filter the query by, both the filter and the query should
  *                contain capture groups with the names in this list
  * @param startFromDoc document to start from, returns hits have doc >= startFromDoc
  * @param startFromToken token to start from, returned hits have doc > startFromDoc or
  *                      start >= startFromToken
  */
class SpanQueryFilterByCaptureGroups(
    _query: SpanQuery,
    _filter: SpanQuery,
    captureGroups: Seq[String],
    startFromDoc: Int = 0,
    startFromToken: Int = 0
) extends SpanQueryBase(_query, _filter) {

  def query: SpanQuery = clauses(0)
  def filter: SpanQuery = clauses(1)

  override def getSpans(context: AtomicReaderContext, acceptDocs: Bits,
    termContexts: util.Map[Term, TermContext]): Spans = {
    val leftSpans = query.getSpans(context, acceptDocs, termContexts)
    val filterSpans = filter.getSpans(context, acceptDocs, termContexts)
    new SpansFilterByCaptureGroups(
      BLSpansWrapper.optWrap(leftSpans),
      BLSpansWrapper.optWrap(filterSpans),
      captureGroups,
      startFromDoc,
      startFromToken
    )
  }

  override def toString(field: String): String = {
    query.toString(field) + s" FILTERED BY <${filter.toString(field)}>"
  }
}
