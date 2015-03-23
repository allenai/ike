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

  "FuzzySequenceSampler" should "Limit queries correctly" in {
    val testQuery = QSeq(Seq(QWord("I"), QCluster("10"), QUnnamed(QWord("???"))))
    val table = buildTable(Seq("mango"), Seq())

    assertResult(Seq("mango", "those")) {
      val hits = FuzzySequenceSampler(1, 1).getSample(testQuery, searcher)
      hitToCaptures(hits, FuzzySequenceSampler.captureGroupName)
    }

    assertResult(Seq("mango")) {
      val hits = FuzzySequenceSampler(1, 1).getLabelledSample(testQuery, searcher, table)
      hitToCaptures(hits, FuzzySequenceSampler.captureGroupName)
    }
  }

  "FuzzySequenceSampler" should "capture misses correctly" in {
    val testQuery = QSeq(Seq(
      QDisj(Seq(QWord("I"), QWord("taste"))),
      QDisj(Seq(QWord("like"), QWord("hate"))),
      QUnnamed(QDisj(Seq(QWord("those"), QWord("great"))))
    ))
    val hits = FuzzySequenceSampler(0, 1).getSample(testQuery, searcher)
    val captures = hitToAllCaptures(hits)
    assertResult(Seq("mango", null, null, "mango"))(captures(0)) // Last word did not match
    assertResult(Seq("those", null, null, null))(captures(1)) // all words matched
    assertResult(Seq("great", null, "not", null))(captures(2)) // middle word did not match
  }
}
