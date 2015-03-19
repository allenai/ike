package org.allenai.dictionary.ml

import org.allenai.dictionary.ml.compoundops.{OpConjunctionOfDisjunctions, OpConjunction, EvaluatedOp}

import scala.collection.JavaConverters._
import org.allenai.common.testkit.{ScratchDirectory, UnitSpec}
import org.allenai.dictionary.index.TestData
import org.allenai.dictionary.ml.QuerySuggester.HitAnalysis
import org.allenai.dictionary.ml.primitveops._
import org.allenai.dictionary._
import org.allenai.dictionary.ml.Label._


import scala.collection.immutable.IntMap

class TestQuerySuggester extends UnitSpec with ScratchDirectory {

  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)

  def buildMap(ints: Seq[Int]) = {
    IntMap[Int](ints.map((_, 0)): _*)
  }

  "SelectOperator" should "Select correct AND query" in {

    def buildExample(k: Int) = {
       Example(if (k == 1) {
         Positive
       } else if(k == -1) {
         Negative
       } else {
         Unknown
       }, 0, "")
    }

    val examples = List(1, 1, 1, 1, 1, -1, -1, -1, -1, -1, 0).
      map(buildExample).toIndexedSeq

    val op1 = SetToken(Prefix(1), QWord("p1"))
    val op2 = SetToken(Prefix(2), QWord("p2"))
    val op3 = SetToken(Prefix(3), QWord("p3"))

    val operatorHits = Map[TokenQueryOp, IntMap[Int]](
      (op1, buildMap(List(1,2,3,4,5,6,7))),
      (op2, buildMap(List(1,2,3,4,5,7,8,9))),
      (op3, buildMap(List(6,7,8,9,10)))
    )
    val data = HitAnalysis(operatorHits, examples)

    val scoredQueries = QuerySuggester.selectOperator(
      data, PositiveMinusNegative(examples, 1),
      (x: EvaluatedOp) => OpConjunction.apply(x),
      3, 2, 3)

    // Best Results is ANDing op1 and op2
    assertResult(Set(
      Set(op1, op2),
      Set(op1),
      Set(op2))) {scoredQueries.map(_.ops.ops).toSet}
  }

  "SelectOperator" should "Select correct OR queries" in {
    val examples = (Seq(Unknown) ++ Seq.fill(5)(Positive) ++
      Seq.fill(5)(Negative) ++ Seq(Unknown)).
      map(x => Example(x, 0, "")).toIndexedSeq

    val op1 = SetToken(Prefix(1), QWord("p1"))
    val op2 = SetToken(Prefix(2), QWord("p2"))
    val op3 = SetToken(Prefix(2), QWord("p3"))
    val op4 = SetToken(Prefix(3), QWord("p4"))

    val operatorHits = Map[TokenQueryOp, IntMap[Int]](
      (op1 -> buildMap(List(1,2,3))),
      (op2 -> buildMap(List(3,4,7,6,8))),
      (op3 -> buildMap(List(1,2,7,6,9))),
      (op4 -> buildMap(List(1,2,3,4,5,10,11)))
    )
    val data = HitAnalysis(operatorHits, examples)
    val scoredQueries = QuerySuggester.selectOperator(
      data, PositiveMinusNegative(examples, 2),
      (x: EvaluatedOp) => OpConjunctionOfDisjunctions(x),
      20, 4, 2)

    assertResult(Set(op2, op3, op4))(scoredQueries.head.ops.ops)
    assertResult(4)(scoredQueries.head.score)
  }

  "BuildHitAnalysis" should "build correctly" in {
    val query = QUnnamed(QDisj(Seq(QWord("mango"), QWord("bananas"), QWord("those"))))
    val hits = searcher.find(BlackLabSemantics.blackLabQuery(query))

    // Double check to make sure things are ordered as we expect
    val hitsFounds = hits.asScala.map(hits.getKwic(_).getMatch("word").get(0))
    assertResult(Seq("mango", "those", "bananas"))(hitsFounds)


    // Get this hits
    val positiveTerms = Set("qwerty", "bananas").map(x => TableRow(Seq(TableValue(Seq(QWord(x))))))
    val negativeTerms = Set("mango").map(x => TableRow(Seq(TableValue(Seq(QWord(x))))))
    val generator = QueryPrefixGenerator(QLeafGenerator(Set(), Set(2)), Seq(1))
    val hitAnalysis = QuerySuggester.buildHitAnalysis(
      hits, Seq(generator), positiveTerms, negativeTerms
    )

    assertResult(Example(Negative, 0, "mango"))(hitAnalysis.examples(0))
    assertResult(Example(Unknown, 0, "those"))(hitAnalysis.examples(1))
    assertResult(Example(Positive, 0, "bananas"))(hitAnalysis.examples(2))
    assertResult(3)(hitAnalysis.examples.size)

    val op10 = SetToken(Prefix(1), QCluster("10"))
    val op11 = SetToken(Prefix(1), QCluster("11"))

    assertResult(Set(op10, op11))(hitAnalysis.operatorHits.keys.toSet)
    assertResult(Set(0, 1))(hitAnalysis.operatorHits.get(op10).get.keySet)
    assertResult(Set(2))(hitAnalysis.operatorHits.get(op11).get.keySet)
  }
}
