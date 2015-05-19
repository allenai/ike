package org.allenai.dictionary.ml.subsample

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary._
import org.allenai.dictionary.index.TestData
import org.allenai.dictionary.ml.TokenizedQuery
import nl.inl.blacklab.search.{ Span, Hits }

import scala.collection.JavaConverters._

class TestFuzzySequenceSampler extends UnitSpec with ScratchDirectory {

  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)

  def hitToCaptures(hits: Hits, captureName: String): Seq[String] = {
    hits.asScala.map(hit => {
      val kwic = hits.getKwic(hit)
      val capture = hits.getCapturedGroupMap(hit).get(captureName)
      val captureKwic = capture.start - hit.start
      kwic.getMatch("word").get(captureKwic)
    }).toSeq
  }

  def hitsToStrings(hits: Hits): Seq[String] = {
    hits.asScala.map(hit => {
      hits.getKwic(hit).getMatch("word").asScala.mkString(" ")
    }).toSeq
  }

  def span2tuple(span: Span): (Int, Int) = (span.start, span.end)

  def buildTable(positive: Seq[String], negative: Seq[String]): Table = {
    Table("testTable", Seq("testCol"),
      positive.map(x => TableRow(Seq(TableValue(x.split(" ").map(QWord.apply))))),
      negative.map(x => TableRow(Seq(TableValue(x.split(" ").map(QWord.apply))))))
  }

  "FuzzySequenceSampler" should "capture misses correctly" in {
    val testQuery = QSeq(Seq(
      QDisj(Seq(QWord("I"), QWord("taste"))),
      QDisj(Seq(QWord("like"), QWord("hate"))),
      QUnnamed(QDisj(Seq(QWord("those"), QWord("great"))))
    ))
    val tokenized = TokenizedQuery.buildFromQuery(testQuery, Seq("c1"))
    val hits = FuzzySequenceSampler(0, 1).getSample(tokenized, searcher,
      Table("", Seq("c1"), Seq(), Seq()), Map())
    // Match I like mango
    assertResult(Seq((2, 3), (0, 1), (1, 2), (-2, -3)))(
      hits.getCapturedGroups(hits.get(0)).map(span2tuple)
    )
    // Match I hate those
    assertResult(Seq((2, 3), (0, 1), (1, 2), (2, 3)))(
      hits.getCapturedGroups(hits.get(1)).map(span2tuple)
    )
    // Match taste not great
    assertResult(Seq((3, 4), (1, 2), (-2, -3), (3, 4)))(
      hits.getCapturedGroups(hits.get(2)).map(span2tuple)
    )
  }

  "FuzzySequenceSampler" should "Limit queries correctly" in {
    val startingQuery = QueryLanguage.parse("({I, hate}) those ({mango, bananas, great})").get
    val table = Table(
      "test",
      Seq("col1", "col2"),
      Seq(
        TableRow(Seq(TableValue(Seq(QWord("I"))), TableValue(Seq(QWord("mango"))))),
        TableRow(Seq(TableValue(Seq(QWord("hate"))), TableValue(Seq(QWord("those")))))
      ),
      Seq(
        TableRow(Seq(TableValue(Seq(QWord("hate"))), TableValue(Seq(QWord("bananas")))))
      )
    )
    val tokenized = TokenizedQuery.buildFromQuery(startingQuery, table.cols)
    val expectedResults = Seq(
      "I like mango",
      "hate those bananas"
    )
    assertResult(expectedResults)(hitsToStrings(FuzzySequenceSampler(0, 1).getLabelledSample(
      tokenized, searcher, table, Map(), 0, 0
    )))
  }
}
