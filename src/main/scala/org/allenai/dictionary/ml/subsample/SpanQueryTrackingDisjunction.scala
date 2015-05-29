package org.allenai.dictionary.ml.subsample

import java.util

import org.apache.lucene.index.{ TermContext, Term, AtomicReaderContext }
import org.apache.lucene.util.Bits

import scala.collection.JavaConverters._
import nl.inl.blacklab.search.lucene.{ BLSpansWrapper, SpanQueryBase }
import org.apache.lucene.search.spans.{ Spans, SpanQuery }

/** Disjunction of SpanQueries that tracks whether firstSpan created each Span it returns of if
  * one of the alternative Spans did so. See SpansTrackingDisjunction
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
