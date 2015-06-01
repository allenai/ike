package org.allenai.dictionary.ml.compoundop

import org.allenai.common.testkit.UnitSpec
import org.allenai.dictionary._
import org.allenai.dictionary.ml._
import org.allenai.dictionary.ml.queryop._

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
    SetToken(QueryToken(3), QPos("r3")),
    List(1, 2, 3, 4, 5, 6, 7)
  )
  val add3 = EvaluatedOp.fromPairs(
    AddToken(3, QWord("a3")),
    List((3, 1), (4, 0), (5, 0))
  )

  "OpConjunctionOfDisjunctions" should "calculate matches correctly" in {
    var op = OpConjunctionOfDisjunctions(suffix1).get.add(replace3)
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

    assertResult(Set(suffix1.op, replace3.op, prefix21.op, prefix22.op, add3.op))(op.ops)
  }

  val replace11 = EvaluatedOp.fromPairs(
    SetToken(QueryToken(1), QWord("r1")),
    List((1, 1), (2, 0), (4, 1), (5, 1), (6, 1))
  )
  val replace12 = EvaluatedOp.fromPairs(
    SetToken(QueryToken(1), QWord("r2")),
    List((3, 0))
  )
  val setMax1 = EvaluatedOp.fromPairs(
    SetMax(1, 1), List((3, 1))
  )

  val remove3 = EvaluatedOp.fromPairs(
    RemoveToken(3), List((1, 1), (2, 0), (3, 0), (4, 0), (5, 0), (6, 0), (7, 0))
  )
  val setMin2 = EvaluatedOp.fromPairs(
    SetMin(2, 1), List((1, 1), (2, 0), (3, 1))
  )
  val replace2 = EvaluatedOp.fromPairs(
    SetToken(QueryToken(2), QWord("r2")), List((2, 1), (3, 0))
  )

  "OpConjunctionOfDisjunctions" should "work with RemoveToken and ModifyParent token" in {
    var op = OpConjunctionOfDisjunctions(replace11).get.add(remove3)

    assertResult(List((1, 2), (2, 0), (4, 1), (5, 1), (6, 1)))(op.numEdits.toSeq.sorted)
    assert(!op.canAdd(replace3.op)) // We remove this slot, so we should not be able to add to it
    assert(!op.canAdd(add3.op))
    assert(!op.canAdd(RemoveToken(1))) // This slot is set, we should not be able to remove it

    op = op.add(setMin2)
    assertResult(List((1, 3), (2, 0)))(op.numEdits.toSeq.sorted)
    assert(!op.canAdd(SetMin(2, 2)))
    assert(!op.canAdd(SetMax(2, 0)))

    op = op.add(replace2)
    assertResult(List((2, 1)))(op.numEdits.toSeq.sorted)

    // We can't add this guy due to the ordering
    assert(!op.canAdd(AddToken(QueryToken(2), QWord(""))))

    // (replace12 OR replace11) AND (removeStar2 AND replace2) AND remove3
    op = op.add(replace12)
    assertResult(List((2, 1), (3, 1)))(op.numEdits.toSeq.sorted)

    // (removePlus1 AND (replace12 OR replace11)) AND (removeStar2 AND replace2) AND remove3
    op = op.add(setMax1)
    assertResult(List((3, 2)))(op.numEdits.toSeq.sorted)

    assertResult(Set(replace11.op, replace12.op, remove3.op, replace2.op,
      setMin2.op, setMax1.op))(op.ops)
  }
}
