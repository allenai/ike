package org.allenai.dictionary.ml.subsample

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary._
import org.allenai.dictionary.index.TestData
import org.allenai.dictionary.ml.TokenizedQuery

import nl.inl.blacklab.search.Hits

import scala.collection.JavaConverters._

class TestMatchesSampler extends UnitSpec with ScratchDirectory {

  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)

  def hitToAllCaptures(hits: Hits, groups: Seq[String]): Seq[Seq[String]] = {
    hits.asScala.map(hit => {
      val kwic = hits.getKwic(hit)
      val captures = groups.map(hits.getCapturedGroupMap(hit).get(_))
      captures.map(span => {
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

  "Matches Sampler" should "get named query correctly" in {
    val query = QueryLanguage.parse("(?<c1> a+) b (?<c2> c d) e*").get
    val tokenized = TokenizedQuery.buildFromQuery(query, Seq("c1", "c2"))
    val names = tokenized.getNames
    val expectedNamedQuery = QSeq(Seq(
      QNamed(QNamed(QPlus(QWord("a")), names(0)), "c1"),
      QWord("b"),
      QNamed(QSeq(Seq(
        QWord("c"),
        QWord("d")
      )), "c2"),
      QNamed(QStar(QWord("e")), names(4))
    ))
    assertResult(expectedNamedQuery)(MatchesSampler.getNamedQuery(tokenized))
  }

  it should "test correctly" in {
    val startingQuery = QueryLanguage.parse("(?<col1> {I, hate, it}) . " +
      "(?<col2> {great, mango, bananas}) .").get
    val table = Table(
      "test",
      Seq("col1", "col2"),
      Seq(
        TableRow(Seq(TableValue(Seq(QWord("I"))), TableValue(Seq(QWord("mango"))))),
        TableRow(Seq(TableValue(Seq(QWord("hate"))), TableValue(Seq(QWord("those")))))
      ),
      Seq(
        TableRow(Seq(TableValue(Seq(QWord("it"))), TableValue(Seq(QWord("great"))))),
        TableRow(Seq(TableValue(Seq(QWord("I"))), TableValue(Seq(QWord("bananas")))))
      )
    )
    val tokenized = TokenizedQuery.buildFromQuery(startingQuery, Seq("col1", "col2"))

    val expectedResults = Seq(
      Seq("I", "mango"),
      Seq("It", "great")
    )
    assertResult(expectedResults)(hitToAllCaptures(MatchesSampler().getLabelledSample(
      tokenized, searcher, table, Map(), Map(), 0, 0
    ), table.cols))
    assertResult(expectedResults.drop(1))(hitToAllCaptures(MatchesSampler().getLabelledSample(
      tokenized, searcher, table, Map(), Map(), 1, 0
    ), table.cols))
  }
}
