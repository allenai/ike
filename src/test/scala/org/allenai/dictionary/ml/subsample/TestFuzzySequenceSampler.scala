package org.allenai.dictionary.ml.subsample

import org.allenai.common.testkit.{ScratchDirectory, UnitSpec}
import org.allenai.dictionary._
import org.allenai.dictionary.index.TestData
import nl.inl.blacklab.search.Hits

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
    hits.asScala.map( hit => {
      hits.getKwic(hit).getMatch("word").asScala.mkString(" ")
    }).toSeq
  }

  def hitToAllCaptures(hits: Hits): Seq[Seq[String]] = {
    hits.asScala.map(hit => {
      val kwic = hits.getKwic(hit)
      hits.getCapturedGroups(hit).map(span => {
        if (span == null) {
          null
        } else {
          val captureKwic = span.start - hit.start
          kwic.getMatch("word").subList(
            captureKwic,
            captureKwic + span.end - span.start
          ).asScala.mkString(" ")
        }
      }).toSeq
    }).toSeq
  }

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
    val hits = FuzzySequenceSampler(0, 1).getSample(testQuery, searcher,
      Table("", Seq("c1"), Seq(), Seq()))
    val captures = hitToAllCaptures(hits)
    assertResult(Seq("mango", null, null, "mango"))(captures(0)) // Last word did not match
    assertResult(Seq("those", null, null, null))(captures(1)) // all words matched
    assertResult(Seq("great", null, "not", null))(captures(2)) // middle word did not match
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
    val expectedResults = Seq(
      "I like mango",
      "hate those bananas"
    )
    assertResult(expectedResults)(hitsToStrings(FuzzySequenceSampler(0, 1).getLabelledSample(
      startingQuery, searcher, table
    )))
  }
}
