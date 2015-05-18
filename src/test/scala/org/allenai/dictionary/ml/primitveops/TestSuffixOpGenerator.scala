package org.allenai.dictionary.ml.primitveops

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary._
import org.allenai.dictionary.index.TestData
import scala.collection.JavaConverters._

class TestSuffixOpGenerator extends UnitSpec with ScratchDirectory {
  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)

  def makeMarkedOpSuffix(i: Int, leaf: QLeaf): MarkedOp = {
    MarkedOp(SetToken(Suffix(i), leaf), required = false)
  }

  "AddSuffixGenerator" should "create correct operators" in {
    val hits = searcher.find(BlackLabSemantics.blackLabQuery(
      QDisj(Seq(QWord("They"), QWord("mango"), QWord(".")))
    ))
    val generator = SuffixOpGenerator(QLeafGenerator(Set("word")), Seq(1, 2, 8))
    val operators = hits.asScala.flatMap(hit => {
      generator.generateOperations(hit, hits)
    }).toSeq

    // Note QWord(".") would be expected, but we blacklist that word atm since
    // it cannot be escaped
    val expectedOperators = Set(
      (1, QWord("taste")),
      (2, QWord("not"))
    ).map(x => makeMarkedOpSuffix(x._1, x._2))
    assertResult(expectedOperators)(operators.toSet)
  }
}
