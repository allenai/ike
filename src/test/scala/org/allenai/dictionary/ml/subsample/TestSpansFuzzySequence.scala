package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.lucene.{BLSpans, HitQueryContext}
import nl.inl.blacklab.search.Span
import org.allenai.common.testkit.{ScratchDirectory, UnitSpec}
import scala.collection.JavaConverters._

class TestFuzzySequence extends UnitSpec with ScratchDirectory {

  def documentEnds(maxDoc: Int=20, length: Int=100): DocFieldLengthGetterStub = {
    new DocFieldLengthGetterStub((0 to maxDoc).map(x => length))
  }

  "FuzzySequence" should "match partial sequences" in {
    val s1 = new SpansStub(Seq((0, 0), (0, 2), (0, 3)), 1)
    val s2 = new SpansStub(Seq((0, 0), (0, 3), (0, 8)), 1)
    val s3 = new SpansStub(Seq((0, 1), (0, 4), (0, 9), (0, 10)), 1)

    val hits = new SpansFuzzySequence(Array(s1, s2, s3), documentEnds(), 2, false)
    assert(hits.next())
    assertResult((2, 5))((hits.start, hits.end)) // Partial match s1 and s2
    assert(hits.next())
    assertResult((7, 10))((hits.start, hits.end)) // partial match s2 and s3
    assert(!hits.next())
  }

  it should "avoid out of bounds matches" in {
    val s1 = new SpansStub(Seq(        (3, 0), (5, 2), (6, 7),         (8, 2)), 1)
    val s2 = new SpansStub(Seq((1, 3), (3, 6), (5, 0), (6, 8), (7, 3)),         2)
    val s3 = new SpansStub(Seq(        (3, 3), (5, 2),                 (8, 6)), 1)

    val hits = new SpansFuzzySequence(Array(s1, s2, s3), documentEnds(length=9), 2, false)
    assert(hits.next())
    assertResult((3, 0, 4))((hits.doc, hits.start, hits.end))
    assert(!hits.next())
  }

  it should "handle overlapping spans" in {
    val s1 = new SpansStub(Seq((0, 1), (0, 2), (0, 4)), 1)
    val s2 = new SpansStub(Seq((0, 1), (0, 2), (0, 3), (0, 4)), 1)
    val s3 = new SpansStub(Seq((0, 1), (0, 5)), 1)

    val hits = new SpansFuzzySequence(Array(s1, s2, s3), documentEnds(), 2, false)
    assert(hits.next())
    assertResult((1, 4))((hits.start, hits.end))
    assert(hits.next())
    assertResult((2, 5))((hits.start, hits.end))
    assert(hits.next())
    assertResult((3, 6))((hits.start, hits.end))
    assert(!hits.next())
  }

  it should "should respect max matches" in {
    val s1 = new SpansStub(Seq((0, 1)), 1)
    val s2 = new SpansStub(Seq((0, 2), (0, 3)), 1)
    val s3 = new SpansStub(Seq((0, 3), (0, 4)), 1)

    val hits = new SpansFuzzySequence(Array(s1, s2, s3),
      documentEnds(), 2, 2, false, Seq(), false)
    assert(hits.next())
    assertResult((2, 5))((hits.start, hits.end))
    assert(!hits.next())
  }

  it should "yield correct capture spans" in {
    val s1 = new SpansStub(Seq((2, 1)), 2)
    val s2 = new SpansStub(Seq((2, 10)), 1)
    val s3 = new SpansStub(Seq((2, 4)), 2)

    val hits = new SpansFuzzySequence(Array(s1, s2, s3), documentEnds(), 2, 10, false,
      Seq(CaptureSpan("1", 0, 1), CaptureSpan("2", 1, 4), CaptureSpan("3", 0, 4)), false)
    val captureGroups = Array[Span](null, null, null)
    val context = new HitQueryContext(hits)
    hits.setHitQueryContext(context)
    assert(hits.next())
    assertResult((1, 6))((hits.start, hits.end))
    hits.getCapturedGroups(captureGroups)
    assertResult((1, 2))((captureGroups(0).start, captureGroups(0).end))
    assertResult((2, 5))((captureGroups(1).start, captureGroups(1).end))
    assertResult((1, 5))((captureGroups(2).start, captureGroups(2).end))
    assert(!hits.next())
  }

  it should "register misses correctly" in {

    // Span currently does not implement equals correctly, so
    // to compare sequences we use toString
    def compareSpans(s1: Seq[Span], s2: Seq[Span]): Boolean = {
      if (s1.size != s2.size) {
        false
      } else {
        (s1 map (x => if (x != null) x.toString)) ==
          (s2 map (x => if (x != null) x.toString))
      }
    }
    val s1 = new SpansStub(Seq((2, 1), (2, 4)), 1)
    val s2 = new SpansStub(Seq((2, 2), (3, 2)), 2)
    val s3 = new SpansStub(Seq((2, 4), (2, 7), (3, 4)), 1)
    val s4 = new SpansStub(Seq((2, 5), (2, 8)), 1)

    val hits = new SpansFuzzySequence(Array(s1, s2, s3, s4),
      documentEnds(), 2, 10, false, Seq(), true)
    var captureGroups = Array[Span](null, null, null, null)
    val context = new HitQueryContext(hits)
    hits.setHitQueryContext(context)
    assert(context.getCapturedGroupNames.asScala ==
      SpansFuzzySequence.getMissesCaptureGroupNames(4))

    // No misses in the first match
    assert(hits.next())
    hits.getCapturedGroups(captureGroups)
    assert(compareSpans(Seq[Span](null, null, null, null), captureGroups))

    // middle misses on the second match
    assert(hits.next())
    hits.getCapturedGroups(captureGroups)
    assert(compareSpans(Seq(null, new Span(5,7), null, null), captureGroups))

    // start and end miss on the third match
    captureGroups = Array[Span](null, null, null, null)
    assert(hits.next())
    hits.getCapturedGroups(captureGroups)
    assert(compareSpans(Seq(new Span(1, 2), null, null, new Span(4, 5)),
      captureGroups))
    assert(!hits.next())
  }

  it should "skip to correctly" in {
    val s1 = new SpansStub(Seq((0, 1), (2, 1), (50, 2)), 1)
    val s2 = new SpansStub(Seq((0, 5), (2, 2), (50, 10)), 1)
    val s3 = new SpansStub(Seq((2, 2), (2, 3), (50, 4)), 1)

    val hits = new SpansFuzzySequence(Array(s1, s2, s3), documentEnds(60), 2, 2, true, Seq(), false)
    assert(hits.skipTo(20))
    assertResult((50, 2, 5))((hits.doc, hits.start, hits.end))
    assert(!hits.next())

    val hits2 = new SpansFuzzySequence(Array(s1, s2, s3), documentEnds(60), 2, 2, true, Seq(), false)
    assert(!hits2.skipTo(51))
  }

  it should "match placeholders correctly" in {
    val spans: Seq[Either[BLSpans, Int]] = Array(
      Right(1),
      Left(new SpansStub(Seq((0, 1), (1, 3)), 2)),
      Right(2),
      Left(new SpansStub(Seq((1, 7)), 1))
    )
    val hits = new SpansFuzzySequence(spans, documentEnds(60), 3, 10, true, Seq(), false)
    assert(hits.next())
    assertResult((0, 0, 6))((hits.doc, hits.start, hits.end))
    assert(hits.next())
    assertResult((1, 2, 8))((hits.doc, hits.start, hits.end))
    assert(!hits.next())
  }
}
