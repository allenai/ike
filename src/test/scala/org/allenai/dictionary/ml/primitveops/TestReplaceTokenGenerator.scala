package org.allenai.dictionary.ml.primitveops

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary._
import org.allenai.dictionary.index.TestData
import scala.collection.JavaConverters._

class TestReplaceTokenGenerator extends UnitSpec with ScratchDirectory {
  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)

  def makeMarkedOpMatch(i: Int, leaf: QLeaf): MarkedOp = {
    MarkedOp(SetToken(Match(i), leaf), required = false)
  }

  "SpecifyQuery" should "build from query correctly" in {

    var testQuery = Seq(QWildcard(), QPos("DT"), QPos("NN"),
      QDisj(Seq(QWord(""), QCluster(""))), QCluster("010"))
    var generator = ReplaceTokenGenerator.specifyTokens(
      testQuery,
      Seq(1, 2, 4, 5), Seq("word", "pos"), Seq(2)
    )
    var expectedGenerator = ReplaceTokenGenerator(Seq(
      (1, QLeafGenerator(Set("word", "pos"), Set(2))),
      (2, QLeafGenerator(Set("word"))),
      (5, QLeafGenerator(Set("word")))
    ))
    assertResult(expectedGenerator)(generator)

    testQuery = Seq(QWildcard(), QPos("DT"))
    generator = ReplaceTokenGenerator.specifyTokens(testQuery, Seq(1, 2), Seq(), Seq(2))
    expectedGenerator = ReplaceTokenGenerator(Seq(
      (1, QLeafGenerator(Set(), Set(2)))
    ))
    assertResult(expectedGenerator)(generator)
  }

  it should "create correct operators" in {
    val captureSeq = Seq(QPos("RB"), QWildcard())
    val query = QSeq(Seq(QWildcard(), QUnnamed(QSeq(captureSeq)), QWildcard()))
    val hits = searcher.find(BlackLabSemantics.blackLabQuery(query))
    val generator = ReplaceTokenGenerator.specifyTokens(
      (query.qexprs.head +: captureSeq) :+ query.qexprs(2),
      Seq(2, 3), Seq("word", "cluster"), Seq(1, 2, 4)
    )

    def getWithContextSize(size: Int): Seq[MarkedOp] = {
      hits.setContextSize(size)
      hits.asScala.flatMap(hit => {
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
}
