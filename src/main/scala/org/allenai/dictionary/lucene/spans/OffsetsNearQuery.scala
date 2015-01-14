package org.allenai.dictionary.lucene.spans

import org.apache.lucene.search.spans.SpanNearQuery
import org.apache.lucene.search.spans.SpanQuery

class OffsetsNearQuery(subQueries: Array[OffsetsSpanQuery], slop: Int = 0, inOrder: Boolean = true)
  extends SpanNearQuery(subQueries.asInstanceOf[Array[SpanQuery]], slop, inOrder) with OffsetsSpanQuery {
  override def getOffsetsSpans: OffsetsSpans = ???
}