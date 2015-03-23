package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.Hits
import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary._
import org.allenai.dictionary.index.TestData

import scala.collection.JavaConverters._

class TestMatchesSampler extends UnitSpec with ScratchDirectory {

  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)

  def hitsToStrings(hits: Hits): Seq[String] = {
    hits.iterator.asScala.map(hit =>
      hits.getKwic(hit).getMatch("word").asScala.mkString(" ")).toSeq
  }

  def buildTable(positive: Seq[String], negative: Seq[String]): Table = {
    Table("testTable", Seq("testCol"),
      positive.map(x => TableRow(Seq(TableValue(x.split(" ").map(QWord.apply))))),
      negative.map(x => TableRow(Seq(TableValue(x.split(" ").map(QWord.apply))))))
  }

  "LimitQuery" should "Convert wildcards captures into a disjunction" in {
    val startingQuery = QSeq(Seq(QWord("the"), QUnnamed(QSeq(Seq(QWildcard(), QWildcard())))))
    val table = buildTable(Seq("a b", "d"), Seq("c", "1 2 3", "a c"))
    val expectedResults = QSeq(Seq(QWord("the"), QUnnamed(QDisj(Seq(
      QSeq(Seq(QWord("a"), QWord("b"))),
      QSeq(Seq(QWord("a"), QWord("c")))
    )))))
    assertResult(expectedResults)(
      MatchesSampler().limitQueryToDictionary(startingQuery, table)
    )
  }

  "MatchesSampler" should "test correctly" in {
    val startingQuery = QUnnamed(QDisj(Seq(QWord("I"), QWord("mango"),
      QWord("not"), QWord("great"))))
    val table = buildTable(Seq("like", "hate", ".*", "not"), Seq("mango", "I"))
    val expectedResults = Seq("I", "mango", "I", "not")

    assertResult(expectedResults)(hitsToStrings(MatchesSampler().getLabelledSample(
      startingQuery, searcher, table
    )))
  }
}
