package org.allenai.dictionary.ml.subsample

import org.allenai.common.testkit.UnitSpec

import nl.inl.blacklab.search.Span
import nl.inl.blacklab.search.lucene.{ BLSpans, HitQueryContext }

class TestSpansTrackingDisjunction extends UnitSpec {

  def assertHit(spans: BLSpans, doc: Int, start: Int, end: Int, didMatch: Boolean) = {
    assert(spans.next())
    assert(spans.doc == doc)
    assert(spans.start == start)
    assert(spans.end == end)

    val captures = Array[Span](null)
    spans.getCapturedGroups(captures)
    if (didMatch) {
      assert(captures.head.start == start)
      assert(captures.head.end == end)
    } else {
      assert(captures.head.start == -start)
      assert(captures.head.end == -end)
    }
  }

  it should "test correctly" in {
    val first = SpansStub(Seq((0, 0, 1), (0, 5, 6), (1, 3, 5)))
    val t1 = SpansStub(Seq((0, 0, 1), (0, 0, 2), (1, 1, 3), (2, 3, 6)))
    val t2 = SpansStub(Seq((0, 5, 6), (2, 3, 5)))
    val spans = new SpansTrackingDisjunction(first, Seq(t1, t2), "c")
    spans.setHitQueryContext(new HitQueryContext(spans))

    assertHit(spans, 0, 0, 1, true)
    assertHit(spans, 0, 0, 2, false)
    assertHit(spans, 0, 5, 6, true)
    assertHit(spans, 1, 1, 3, false)
    assertHit(spans, 1, 3, 5, true)
    assertHit(spans, 2, 3, 5, false)
    assertHit(spans, 2, 3, 6, false)
  }
}
