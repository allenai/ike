package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.{ QueryExecutionContext, TextPatternTranslator, TextPattern }
import org.apache.lucene.search.spans.SpanQuery

/** TextPattern for SpanQueryTrackingDisjunction. Note this only works when being translated into
  * SpanQueries, making it work in general would require an API change to TextPatternTranslator
  */
class TextPatternTrackingDisjunction(
    firstSpan: TextPattern,
    alternatives: Seq[TextPattern],
    captureName: String
) extends TextPattern {

  override def translate[T](
    translator: TextPatternTranslator[T],
    context: QueryExecutionContext
  ): T = {
    val translatedFirstSpan = firstSpan.translate(translator).asInstanceOf[SpanQuery]
    val translatedAlternatives = alternatives.map(_.translate(translator).asInstanceOf[SpanQuery])
    new SpanQueryTrackingDisjunction(
      translatedFirstSpan, translatedAlternatives, captureName
    ).asInstanceOf[T]
  }
}
