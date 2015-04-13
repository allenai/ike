package org.allenai.dictionary.ml.compoundops

import org.allenai.common.testkit.UnitSpec
import org.allenai.dictionary._
import org.allenai.dictionary.ml.{QueryToken, Prefix, Suffix}
import org.allenai.dictionary.ml.primitveops._

class TestOpConjunctionOfDisjunctions extends UnitSpec {

  val suffix1 = EvaluatedOp.fromPairs(
    SetToken(Suffix(1), QWord("s1")),
    List((1, 1), (2, 0), (3, 0), (4, 1), (5, 1), (6, 1))
  )
  val prefix21 = EvaluatedOp.fromPairs(
    SetToken(Prefix(2), QWord("p21")),
    List((1, 1), (2, 1), (8, 0))
  )
  val prefix22 = EvaluatedOp.fromPairs(
    SetToken(Prefix(2), QWord("p22")),
    List((1, 1), (3, 0), (4, 1), (9, 1))
  )
  val replace3 = EvaluatedOp.fromList(
    SetToken(QueryToken(3), QCluster("r3")),
    List(1, 2, 3, 4, 5, 6, 7)
  )
  val add3 = EvaluatedOp.fromPairs(
    AddToken(3, QWord("a3")),
    List((3, 1), (4, 0), (5, 0))
  )

  var op = OpConjunctionOfDisjunctions(suffix1).add(replace3)

  "OpConjunctionOfDisjunctions" should "calculate matches correctly" in {
    // Suffix1 AND replace3
    assertResult(List((1, 1), (2, 0), (3, 0), (4, 1), (5, 1), (6, 1)))(op.numEdits.toSeq.sorted)
    op = op.add(prefix21)
    // Suffix1 AND replace3 AND prefix21
    assertResult(List((1, 2), (2, 1)))(op.numEdits.toSeq.sorted)
    op = op.add(prefix22)
    // Suffix1 AND replace3 AND (prefix21 OR prefix22)
    assertResult(List((1, 2), (2, 1), (3, 0), (4, 2)))(op.numEdits.toSeq.sorted)
    op = op.add(add3)
    // Suffix1 AND (replace3 OR add3) AND (prefix21 OR prefix22)
    assertResult(List((1, 2), (2, 1), (3, 1), (4, 2)))(op.numEdits.toSeq.sorted)
  }
}
