package org.allenai.dictionary.ml

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary._
import org.allenai.dictionary.index.TestData
import org.allenai.dictionary.ml.Label._
import org.allenai.dictionary.ml.queryop._
import org.allenai.dictionary.ml.subsample.GeneralizedQuerySampler

import scala.collection.immutable.IntMap
import scala.collection.JavaConverters._

class TestHitAnalyzer extends UnitSpec with ScratchDirectory {
  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)

  def testWeightedSample(wex: WeightedExample, label: Label, requiredEdits: Int, doc: Int): Unit = {
    assertResult(label)(wex.label)
    assertResult(wex.requiredEdits)(requiredEdits)
    assertResult(wex.doc)(doc)
  }

  "GetExamples" should "label correctly" in {
    val query = QueryLanguage.parse("(?<c1> {mango, bananas, not})").get
    val table = Table("test", Seq("c1"),
      Seq(TableRow(Seq(TableValue(Seq(QWord("mango")))))),
      Seq(TableRow(Seq(TableValue(Seq(QWord("not")))))))
    val hits = searcher.find(BlackLabSemantics.blackLabQuery(query))
    assert(hits.size() == 3)
    assertResult(Seq(Positive, Unlabelled, Negative)) {
      HitAnalyzer.getExamples(
        TokenizedQuery.buildFromQuery(query, table.cols),
        hits, table
      ).map(_.label)
    }
  }

  def token(word: String): Token = {
    val pos = TestData.posTags(word)
    Token(word.toLowerCase, pos)
  }

  "getMatchData" should "test correctly" in {
    val query = QueryLanguage.parse("{it, those, They} (?<c1> .)").get
    val tokenized = TokenizedQuery.buildFromQuery(query, Seq())
    val hits = searcher.find(BlackLabSemantics.blackLabQuery(query))
    hits.setContextSize(1)
    hits.setContextField(List("word", "pos").asJava)

    val matchData = HitAnalyzer.getMatchData(hits, tokenized, 1, 2)
    assertResult(QueryMatches(QuerySlotData(Prefix(1)), Seq(
      QueryMatch(List(), true),
      QueryMatch(List(token("hate")), true),
      QueryMatch(List(), true)
    )))(matchData(0))
    assertResult(QueryMatches(QuerySlotData(
      Some(query.asInstanceOf[QSeq].qexprs(0)),
      QueryToken(1), false
    ), Seq(
      QueryMatch(List(token("It")), true),
      QueryMatch(List(token("those")), true),
      QueryMatch(List(token("They")), true)
    )))(matchData(1))
    assertResult(QueryMatches(QuerySlotData(Some(QWildcard()), QueryToken(2), true), Seq(
      QueryMatch(List(token("tastes")), true),
      QueryMatch(List(token("bananas")), true),
      QueryMatch(List(token("taste")), true)
    )))(matchData(2))
    assertResult(QueryMatches(QuerySlotData(Suffix(1)), Seq(
      QueryMatch(List(token("great")), true),
      QueryMatch(List(token(".")), true),
      QueryMatch(List(token("not")), true)
    )))(matchData(3))
    assertResult(QueryMatches(QuerySlotData(Suffix(2)), Seq(
      QueryMatch(List(token(".")), true),
      QueryMatch(List(), true),
      QueryMatch(List(token("great")), true)
    )))(matchData(4))
    assertResult(5)(matchData.size)
  }

  "BuildHitAnalysis" should "build correctly" in {
    val query = QNamed(QDisj(Seq(QWord("mango"), QWord("bananas"), QWord("those"))), "c1")
    val hits = searcher.find(BlackLabSemantics.blackLabQuery(query))

    // Double check to make sure things are ordered as we expect
    val hitsFounds = hits.asScala.map(hits.getKwic(_).getMatch("word").get(0))
    assertResult(Seq("mango", "those", "bananas"))(hitsFounds)

    val table = Table("test", Seq("c1"),
      Seq("qwerty", "bananas").map(x => TableRow(Seq(TableValue(Seq(QWord(x)))))),
      Seq("mango").map(x => TableRow(Seq(TableValue(Seq(QWord(x)))))))

    // Get the hits
    val hitAnalysis = HitAnalyzer.buildHitAnalysis(
      Seq(hits), TokenizedQuery.buildFromQuery(query, Seq()),
      1, 0, SpecifyingOpGenerator(true, false),
      table
    )

    testWeightedSample(hitAnalysis.examples(0), Negative, 0, 0)
    testWeightedSample(hitAnalysis.examples(1), Unlabelled, 0, 2)
    testWeightedSample(hitAnalysis.examples(2), Positive, 0, 2)
    assertResult(3)(hitAnalysis.examples.size)

    val op10 = SetToken(Prefix(1), QPos("VBP"))
    val op11 = SetToken(Prefix(1), QPos("DT"))
    def rd(word: String): QueryOp = RemoveFromDisj(QueryToken(1), QWord(word))
    val removeFromDisjOps = Seq(
      rd("mango"), rd("those"), rd("bananas")
    )

    assertResult(Set(op10, op11) ++ removeFromDisjOps)(hitAnalysis.operatorHits.keySet)
    assertResult(Set(0, 1))(hitAnalysis.operatorHits.get(op10).get.keySet)
    assertResult(Set(2))(hitAnalysis.operatorHits.get(op11).get.keySet)
    assertResult(Set(1, 2))(hitAnalysis.operatorHits.get(rd("mango")).get.keySet)
    assertResult(Set(0, 2))(hitAnalysis.operatorHits.get(rd("those")).get.keySet)
    assertResult(Set(0, 1))(hitAnalysis.operatorHits.get(rd("bananas")).get.keySet)
  }
}
