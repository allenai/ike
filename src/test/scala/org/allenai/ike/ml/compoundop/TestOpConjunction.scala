package org.allenai.ike.ml.compoundop

import org.allenai.common.testkit.UnitSpec
import org.allenai.ike.ml.queryop._
import org.allenai.ike.ml.{ Prefix, QueryToken }
import org.allenai.ike.{ QPos, QWord }

class TestOpConjunction extends UnitSpec {

  val prefix2 = EvaluatedOp.fromPairs(
    SetToken(Prefix(2), QWord("p2")),
    List((1, 1), (3, 0), (4, 1), (9, 1))
  )
  val replace3 = EvaluatedOp.fromList(
    SetToken(QueryToken(3), QPos("r3")),
    List(1, 2, 3, 4, 5)
  )
  val setMin3 = EvaluatedOp.fromPairs(
    SetMin(3, 1), List((1, 1), (3, 0))
  )
  val setMax3 = EvaluatedOp.fromPairs(
    SetMax(3, 1), List((1, 1), (3, 0))
  )
  val setMinLarge3 = EvaluatedOp.fromPairs(
    SetMin(3, 2), List((1, 1), (3, 0))
  )

  val removeToken = EvaluatedOp.fromPairs(RemoveToken(1), List((1, 1), (5, 0)))

  "OpConjunction" should "calculate numEdits correctly" in {
    var op = OpConjunction(replace3).get.add(prefix2)
    assertResult(List((1, 1), (3, 0), (4, 1)))(op.numEdits.toSeq.sorted)

    assert(!op.canAdd(prefix2.op))

    op = op.add(setMin3).add(setMax3)
    assertResult(List((1, 1), (3, 0)))(op.numEdits.toSeq.sorted)

    assert(!op.canAdd(setMax3.op))
    assert(!op.canAdd(setMin3.op))
    assert(!op.canAdd(setMinLarge3.op))

    op = op.add(removeToken)
    assertResult(List((1, 2)))(op.numEdits.toSeq.sorted)

    assertResult(5)(op.size)
    assertResult(Set(prefix2.op, setMin3.op, setMax3.op, replace3.op, RemoveToken(1)))(op.ops)
  }
}
