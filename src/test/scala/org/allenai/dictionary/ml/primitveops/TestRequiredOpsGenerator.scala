package org.allenai.dictionary.ml.primitveops

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary.index.TestData
import org.allenai.dictionary.ml.QueryToken
import org.allenai.dictionary.ml.subsample.{ SpansFuzzySequence, FuzzySequenceSampler }
import org.allenai.dictionary._
import scala.collection.JavaConverters._

class TestRequiredOpsGenerator extends UnitSpec with ScratchDirectory {
  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)

  def makeMarkedOpMatch(i: Int, leaf: QLeaf, required: Boolean = false): MarkedOp = {
    MarkedOp(SetToken(QueryToken(i), leaf), required)
  }

  "SetCaptureToken" should "create correct operators" in {
    val query = QSeq(Seq(
      QDisj(Seq(QWord("I"), QWord("hate"))),
      QUnnamed(QWord("like")),
      QWord("bananas")
    ))
    val hits = FuzzySequenceSampler(1, 1).getSample(query, searcher, Table("", Seq(""),
      Seq(), Seq()))
    hits.get(0) // Ensure Hits loads up the captureGroupNames by requesting the first hit
    val captureGroups = hits.getCapturedGroupNames
    val captureIndices = SpansFuzzySequence.getMissesCaptureGroupNames(3).
      map(x => captureGroups.indexOf(x))
    val generator = RequiredEditsGenerator(
      QLeafGenerator(Set("pos")),
      QLeafGenerator(Set()),
      captureIndices
    )

    def testWithContextSize(contextSize: Int): Unit = {
      hits.setContextSize(contextSize)
      val operators = hits.asScala.flatMap(hit => {
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
      println(operators)
      expectedOperators.zipWithIndex.foreach {
        case (op, index) =>
          assert(operators.size > index, "Ran out of operators")
          assertResult(op, s"Error on operator number $index")(operators(index))
      }
    }
    testWithContextSize(0)
    testWithContextSize(5)
  }
}
