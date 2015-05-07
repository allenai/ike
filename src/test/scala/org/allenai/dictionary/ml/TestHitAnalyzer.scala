package org.allenai.dictionary.ml

import org.allenai.common.testkit.{ScratchDirectory, UnitSpec}
import org.allenai.dictionary._
import org.allenai.dictionary.index.TestData
import org.allenai.dictionary.ml.Label._
import org.allenai.dictionary.ml.queryop._
import org.allenai.dictionary.ml.subsample.FuzzySequenceSampler

import scala.collection.immutable.IntMap
import scala.collection.JavaConverters._

class TestHitAnalyzer  extends UnitSpec with ScratchDirectory {
  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)

  "GetExamples" should "label correctly" in {
    val query = QueryLanguage.parse("(?<c1> {mango, bananas, not})").get
    val table = Table("test", Seq("c1"),
      Seq(TableRow(Seq(TableValue(Seq(QWord("mango")))))),
      Seq(TableRow(Seq(TableValue(Seq(QWord("not"))))))
    )
    val hits = searcher.find(BlackLabSemantics.blackLabQuery(query))
    assert(hits.size() == 3)
    assertResult(Seq(Positive, Unlabelled, Negative)) {
      HitAnalyzer.getExamples(TokenizedQuery.buildFromQuery(query), hits, table).map(_.label)
    }
  }

  def buildToken(word: String): Token = {
    val pos = TestData.posTags(word)
    val cluster = TestData.clusters(word)
    Token(word, pos, cluster)
  }

  "getMatchData" should "test correctly" in {
    val query = QueryLanguage.parse("{it, those, They} (?<c1> .)").get
    val tokenized = TokenizedQuery.buildFromQuery(query)
    val hits = searcher.find(BlackLabSemantics.blackLabQuery(query))
    hits.setContextSize(1)
    hits.setContextField(List("word", "pos").asJava)

    val matchData = HitAnalyzer.getMatchData(hits, tokenized, 1, 2)
    assertResult(QueryMatches(QuerySlotData(Prefix(1)), Seq(
      QueryMatch(List(), true),
      QueryMatch(List(buildToken("hate")), true),
      QueryMatch(List(), true)
    )))(matchData(0))
    assertResult(QueryMatches(QuerySlotData(Some(query.asInstanceOf[QSeq].qexprs(0)),
      QueryToken(1), false, true, false), Seq(
        QueryMatch(List(buildToken("It")), true),
        QueryMatch(List(buildToken("those")), true),
        QueryMatch(List(buildToken("They")), true)
      )))(matchData(1))
    assertResult(QueryMatches(QuerySlotData(Some(QWildcard()), QueryToken(2),
      true, false, false), Seq(
      QueryMatch(List(buildToken("tastes")), true),
      QueryMatch(List(buildToken("bananas")), true),
      QueryMatch(List(buildToken("taste")), true)
    )))(matchData(2))
    assertResult(QueryMatches(QuerySlotData(Suffix(1)), Seq(
      QueryMatch(List(buildToken("great")), true),
      QueryMatch(List(buildToken(".")), true),
      QueryMatch(List(buildToken("not")), true)
    )))(matchData(3))
    assertResult(QueryMatches(QuerySlotData(Suffix(2)), Seq(
      QueryMatch(List(buildToken(".")), true),
      QueryMatch(List(), true),
      QueryMatch(List(buildToken("great")), true)
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
      Seq("mango").map(x => TableRow(Seq(TableValue(Seq(QWord(x))))))
    )

    // Get the hits
    val hitAnalysis = HitAnalyzer.buildHitAnalysis(
      Seq(hits), TokenizedQuery.buildFromQuery(query),
      1, 0, SpecifyingOpGenerator(false, false, Seq(2)),
      table
    )

    // What gets put in str changes for debug reasons so best to just ignore it
    assertResult(WeightedExample(Negative, 0, 1.0, 0))(hitAnalysis.examples(0).copy(str=""))
    assertResult(WeightedExample(Unlabelled, 0, 1.0, 2))(hitAnalysis.examples(1).copy(str=""))
    assertResult(WeightedExample(Positive, 0, 1.0, 2))(hitAnalysis.examples(2).copy(str=""))
    assertResult(3)(hitAnalysis.examples.size)

    val op10 = SetToken(Prefix(1), QCluster("10"))
    val op11 = SetToken(Prefix(1), QCluster("11"))

    assertResult(Set(op10, op11))(hitAnalysis.operatorHits.keySet)
    assertResult(Set(0, 1))(hitAnalysis.operatorHits.get(op10).get.keySet)
    assertResult(Set(2))(hitAnalysis.operatorHits.get(op11).get.keySet)
  }

  it should "build from fuzzy sequences hits correctly" in {
    val query = QueryLanguage.parse("(?<c2> I) running (?<c1> {mango, blarg})").get
    val tokenized = TokenizedQuery.buildFromQuery(query)
    val table = Table("test", Seq("c1", "c2"),
      Seq(Seq("like", "mango"), Seq("mango", "i")).map { x =>
        TableRow(x.map(y => TableValue(Seq(QWord(y)))))
      },
      Seq()
    )
    val hits = FuzzySequenceSampler(1, 1).getLabelledSample(tokenized, searcher, table, Map(), 0)

    val hitAnalysis = HitAnalyzer.buildHitAnalysis(
      Seq(hits),
      TokenizedQuery.buildFromQuery(query),
      0, 0, GeneralizingOpGenerator(true, true, Seq(), false, tokenized.size, true), table
    )
    assertResult(IndexedSeq(WeightedExample(Positive, 1, 1.0, 0)))(
      hitAnalysis.examples.map(_.copy(str="")))
    // Note VBP will not be suggested because it does not generalize 'running'
    val opMap = hitAnalysis.operatorHits
    val op2 = SetToken(QueryToken(1), QPos("PRP"))
    assertResult(Set(op2))(opMap.keySet)
    assertResult(IntMap((0, 0)))(opMap(op2))
  }
}
