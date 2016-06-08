package org.allenai.ike.ml.subsample

import org.allenai.blacklab.search.lucene.{ BLSpansWrapper, SpanQueryBase }
import org.apache.lucene.index.{ AtomicReaderContext, Term, TermContext }
import org.apache.lucene.search.spans.{ SpanQuery, Spans }
import org.apache.lucene.util.Bits

import java.util
import scala.collection.JavaConverters._

/** Disjunction of SpanQueries that uses a capture group to mark whether 'firstSpan' created each
  * returned Span or if one of the 'alternatives' Spans did. See `SpansTrackingDisjunction`.
  */
class SpanQueryTrackingDisjunction(
    firstSpan: SpanQuery,
    alternatives: Seq[SpanQuery],
    captureName: String
) extends SpanQueryBase((firstSpan +: alternatives).asJava) {

  override def getSpans(
    atomicReaderContext: AtomicReaderContext,
    bits: Bits, map: util.Map[Term, TermContext]
  ): Spans = {
    val spans = clauses.map(spanQuery =>
      BLSpansWrapper.optWrap(spanQuery.getSpans(atomicReaderContext, bits, map)))
    new SpansTrackingDisjunction(spans.head, spans.drop(1), captureName)
  }

  override def toString(s: String): String = {
    s"${firstSpan.toString(s)} EXTENDED BY<$captureName ${alternatives.map(_.toString(s))})"
  }
}
