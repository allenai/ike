package org.allenai.dictionary.ml.subsample

import org.allenai.common.testkit.{UnitSpec, ScratchDirectory}
import org.allenai.dictionary.{BlackLabSemantics, QWord, QueryLanguage}
import org.allenai.dictionary.index.TestData
import org.apache.lucene.search.spans.SpanQuery
import scala.collection.JavaConverters._

class TestSpanQueryFilterByCaptureGroups extends UnitSpec with ScratchDirectory {
  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)

  "SpanQueryFilterByCaptureGroups" should "filter correctly" in {
    val startingQuery = QueryLanguage.parse("(?<c1> {like, mango, taste, I}) . " +
        "(?<c2> {mango, great})").get
    val startingSpanQuery = searcher.createSpanQuery(BlackLabSemantics.blackLabQuery(startingQuery))
    val andWith = QueryLanguage.parse("(?<c1> {I, taste}) . (?<c2> {mango, great})").get
    val andWithSpanQuery = searcher.createSpanQuery(BlackLabSemantics.blackLabQuery(andWith))

    def testQuery(query: SpanQuery, results: Seq[String]) = {
      val hits = searcher.find(query)
      assertResult(results)(
        hits.asScala.map(hit => hits.getKwic(hit).getMatch("word").asScala.mkString(" ")))
    }

    testQuery(new SpanQueryFilterByCaptureGroups(startingSpanQuery, andWithSpanQuery,
      Seq("c1", "c2")), Seq("I like mango", "taste not great"))

    testQuery(new SpanQueryFilterByCaptureGroups(startingSpanQuery, andWithSpanQuery,
      Seq("c1", "c2"), 1), Seq("taste not great"))

    testQuery(new SpanQueryFilterByCaptureGroups(startingSpanQuery, andWithSpanQuery,
      Seq("c1", "c2"), 0, 3), Seq("taste not great"))
  }
}
