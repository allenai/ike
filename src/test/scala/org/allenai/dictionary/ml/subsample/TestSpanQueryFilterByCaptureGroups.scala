package org.allenai.dictionary.ml.subsample

import org.allenai.common.testkit.{UnitSpec, ScratchDirectory}
import org.allenai.dictionary.{BlackLabSemantics, QWord, QueryLanguage}
import org.allenai.dictionary.index.TestData
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
    val query = new SpanQueryFilterByCaptureGroups(
      startingSpanQuery,
      andWithSpanQuery,
      Seq("c1", "c2"))
    val hits = searcher.find(query)
    assertResult(Seq(
      "I like mango",
      "taste not great"
    ))(hits.asScala.map(hit => hits.getKwic(hit).getMatch("word").asScala.mkString(" ")))
  }
}
