package org.allenai.dictionary.ml.subsample

import java.util

import nl.inl.blacklab.search.lucene.{ BLSpansWrapper, SpanQueryBase }
import org.apache.lucene.index.{ TermContext, Term, AtomicReaderContext }
import org.apache.lucene.search.spans.{ Spans, SpanQuery }
import org.apache.lucene.util.Bits

/** SpanQuery that filters another query of hits that return too few valid captures, where a valid
  * capture is a capture that is non-negative and non-null. See SpansMinimumValidCaptures.
  */
class SpanQueryMinimumValidCaptures(
    spans: SpanQuery,
    requiredMatches: Int,
    groupsToCheck: Seq[String]
) extends SpanQueryBase(spans) {

  override def getSpans(atomicReaderContext: AtomicReaderContext, bits: Bits,
    map: util.Map[Term, TermContext]): Spans = {
    val spans = BLSpansWrapper.optWrap(clauses.head.getSpans(atomicReaderContext, bits, map))
    new SpansMinimumValidCaptures(spans, requiredMatches, groupsToCheck)
  }

  override def toString(s: String): String = {
    s"AtLeast($requiredMatches) Captures From ${spans.toString(s)}"
  }
}
