package org.allenai.ike.ml.subsample

import org.allenai.common.testkit.UnitSpec

import nl.inl.blacklab.search.Span
import nl.inl.blacklab.search.lucene.HitQueryContext

class TestMinimumValidCaptures extends UnitSpec {
  def s(spans: Int*) = spans.map { x => new Span(x, x + x / Math.abs(x)) }

  it should "test correctly" in {
    val captureNames = Seq("c1", "c2", "c3", "c4")
    val stub = SpansStub.withCaptures(Seq(
      (0, 1, 3),
      (0, 2, 6),
      (0, 2, 7),
      (2, 2, 5),
      (10, 6, 10)
    ), Seq(
      s(1, 1, -1, 1),
      s(1, 1, 1, 1),
      s(-1, -1, -1, 1),
      s(-1, 1, -1, 1),
      s(1, 1, 1, 1)
    ), captureNames)
    val validated = new SpansMinimumValidCaptures(
      stub, 2, Seq("c1", "c2", "c3")
    )
    val context = new HitQueryContext(validated)
    validated.setHitQueryContext(context)

    def testAtHit(at: Int): Unit = {
      assert(validated.next())
      val expected = (stub.docs(at), stub.starts(at), stub.ends(at))
      val actual = (validated.doc, validated.start, validated.end)
      assertResult(expected)(actual)
      val expectedC = captureNames.map { name =>
        stub.captures(at)(context.getCapturedGroupNames.indexOf(name))
      }

      val actualC = Array.fill[Span](context.numberOfCapturedGroups)(null)
      stub.getCapturedGroups(actualC)
      assertResult(expectedC)(actualC)
    }

    testAtHit(0)
    testAtHit(1)
    testAtHit(4)
    assert(!validated.next())
  }
}
