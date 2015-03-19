package org.allenai.dictionary.ml.primitveops

import org.allenai.common.testkit.{ScratchDirectory, UnitSpec}
import org.allenai.dictionary._
import org.allenai.dictionary.index.TestData
import org.allenai.dictionary.ml.TokenizedQuery
import org.allenai.dictionary.ml.subsample.{SpansFuzzySequence, FuzzySequenceSampler}

import scala.collection.JavaConversions._


class TestQueryGenerators extends UnitSpec with ScratchDirectory {
  TestData.createTestIndex(scratchDir)

  def makeMarkedOpMatch(i: Int, leaf: QLeaf, required: Boolean = false): MarkedOp = {
    MarkedOp(SetToken(Match(i), leaf), required)
  }
  def makeMarkedOpPrefix(i: Int, leaf: QLeaf): MarkedOp = {
    MarkedOp(SetToken(Prefix(i), leaf), false)
  }
  def makeMarkedOpSuffix(i: Int, leaf: QLeaf): MarkedOp = {
    MarkedOp(SetToken(Suffix(i), leaf), false)
  }
  val searcher = TestData.testSearcher(scratchDir)

  "AddPrefixGenerator" should "create correct operators" in {
    val hits = searcher.find(BlackLabSemantics.blackLabQuery(
      QDisj(Seq(QWord("not"), QWord("bananas")))))
    val generator = QueryPrefixGenerator(QLeafGenerator(Set("pos", "word")), Seq(1, 3, 8))
    val operators = hits.flatMap(hit => {
        generator.generateOperations(hit, hits)
    }).toSeq

    val expectedOperators = Set(
      (1, QWord("those")),
      (3, QWord("I")),
      (1, QPos("DT")),
      (3, QPos("PRP")),
      (1, QWord("taste")),
      (1, QPos("VBP"))
    ).map(x => makeMarkedOpPrefix(x._1, x._2))
    assertResult(expectedOperators)(operators.toSet)
  }

  "AddSuffixGenerator" should "create correct operators" in {
    val hits = searcher.find(BlackLabSemantics.blackLabQuery(
      QDisj(Seq(QWord("They"), QWord("mango"), QWord(".")))))
    val generator = QuerySuffixGenerator(QLeafGenerator(Set("word"), Set(2)), Seq(1, 2, 8))
    val operators = hits.flatMap(hit => {
      generator.generateOperations(hit, hits)
    }).toSeq

    val expectedOperators = Set(
      (1, QWord("taste")),
      (2, QWord("not")),
      (1, QWord(".")),
      (1, QCluster("10")),
      (2, QCluster("11")),
      (1 ,QCluster("11"))
    ).map(x => makeMarkedOpSuffix(x._1, x._2))
    assertResult(expectedOperators)(operators.toSet)
  }

  val allTags = Seq("pos", "cluster", "word")
  "SpecifyQuery" should "build from query correctly" in {

    var testQuery = Seq(QWildcard(), QPos("DT"), QPos("NN"),
      QDisj(Seq(QWord(""), QCluster(""))), QCluster("010"))
    var generator = ReplaceTokenGenerator.specifyTokens(testQuery, Seq(1,2,4,5), Seq("word", "pos"), Seq(2))
    var expectedGenerator = ReplaceTokenGenerator(Seq(
      (1, QLeafGenerator(Set("word", "pos"), Set(2))),
      (2, QLeafGenerator(Set("word"))),
      (5, QLeafGenerator(Set("word")))
    ))
    assertResult(expectedGenerator)(generator)

    testQuery = Seq(QWildcard(), QPos("DT"))
    generator = ReplaceTokenGenerator.specifyTokens(testQuery, Seq(1, 2), Seq(), Seq(2))
    expectedGenerator = ReplaceTokenGenerator(Seq(
      (1, QLeafGenerator(Set(), Set(2)))))
      assertResult(expectedGenerator)(generator)
  }

  it should "create correct operators" in {
    val captureSeq = Seq(QPos("RB"), QWildcard())
    val query = QSeq(Seq(QWildcard(), QUnnamed(QSeq(captureSeq)), QWildcard()))
    val hits = searcher.find(BlackLabSemantics.blackLabQuery(query))
    val generator = ReplaceTokenGenerator.specifyTokens(((query.qexprs(0) +: captureSeq) :+ query.qexprs(2)),
      Seq(2, 3), Seq("word", "cluster"), Seq(1, 2, 4))

    def getWithContextSize(size: Int): Seq[MarkedOp] = {
      hits.setContextSize(size)
      hits.flatMap(hit => {
        generator.generateOperations(hit, hits)
      }).toSeq
    }

    val expectedOperators = Set(
      (2, QWord("not")),
      (3, QWord("great")),
      (3, QCluster("11")),
      (3, QCluster("1"))
    ).map(x => makeMarkedOpMatch(x._1, x._2))
    // Test with different context sizes to check for indexing problems
    assertResult(expectedOperators)(getWithContextSize(0).toSet)
    assertResult(expectedOperators)(getWithContextSize(5).toSet)
  }

  "SetCaptureToken" should "create correct operators" in {
    val query = QSeq(Seq(
      QDisj(Seq(QWord("I"), QWord("hate"))),
      QUnnamed(QWord("like")),
      QWord("bananas")))
    val hits = FuzzySequenceSampler(1, 1, "capture").getRandomSample(query, searcher)
    hits.get(0) // Ensure Hits loads up the captureGroupNames by requesting the first hit
    val captureGroups = hits.getCapturedGroupNames()
    val captureIndices = SpansFuzzySequence.getMissesCaptureGroupNames(3).map(x => captureGroups.indexOf(x))
    val generator = RequiredEditsGenerator(
      QLeafGenerator(Set("pos")),
      QLeafGenerator(Set()),
      captureIndices
    )

    def testWithContextSize(contextSize: Int) = {
      hits.setContextSize(contextSize)
      val operators = hits.flatMap(hit => {
        generator.generateOperations(hit, hits)
      }).toSeq

      val expectedOperators = Seq(
        (1, QPos("PRP"), false), // 'I' on the first match
        (2, QPos("VBP"), false), // 'like' on the first match
        (3, QPos("NN"), true), // 'Mango' on the first match
        (1, QPos("VBP"), false), // 'hate' on the second match
        (2, QPos("DT"), true), // 'those' on the second match
        (3, QPos("NNS"), false) // 'bananas' on the second match
      ).map(x => makeMarkedOpMatch(x._1, x._2, x._3))

      expectedOperators.zipWithIndex.foreach { case (op, index) => {
        assert(operators.size > index)
        assertResult(op, s"Error on operator number $index")(operators(index))
      }}
    }
    testWithContextSize(0)
    testWithContextSize(5)
  }
}
