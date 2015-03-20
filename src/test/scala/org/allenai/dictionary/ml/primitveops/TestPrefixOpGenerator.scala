package org.allenai.dictionary.ml.primitveops

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary._
import org.allenai.dictionary.index.TestData
import scala.collection.JavaConverters._

class TestPrefixOpGenerator extends UnitSpec with ScratchDirectory {
  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)

  def makeMarkedOpPrefix(i: Int, leaf: QLeaf): MarkedOp = {
    MarkedOp(SetToken(Prefix(i), leaf), required = false)
  }

  "AddPrefixGenerator" should "create correct operators" in {
    val hits = searcher.find(BlackLabSemantics.blackLabQuery(
      QDisj(Seq(QWord("not"), QWord("bananas")))
    ))
    val generator = PrefixOpGenerator(QLeafGenerator(Set("pos", "word")), Seq(1, 3, 8))
    val operators = hits.asScala.flatMap(hit => {
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
}
