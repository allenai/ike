package org.allenai.dictionary.lucene.spans

import org.apache.lucene.search.spans.SpanQuery

trait OffsetsSpanQuery extends SpanQuery {
  def getOffsetsSpans: OffsetsSpans
}