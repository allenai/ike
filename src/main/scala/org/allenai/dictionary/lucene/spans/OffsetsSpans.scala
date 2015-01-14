package org.allenai.dictionary.lucene.spans

import org.apache.lucene.search.spans.Spans
import org.allenai.common.immutable.Interval

trait OffsetsSpans extends Spans {
  def offsets: Seq[Interval]
}