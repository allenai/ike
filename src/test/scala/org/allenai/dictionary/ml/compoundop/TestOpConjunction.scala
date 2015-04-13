package org.allenai.dictionary.ml.compoundop

import org.allenai.common.testkit.UnitSpec
import org.allenai.dictionary.{QCluster, QWord}
import org.allenai.dictionary.ml.{QueryToken, Prefix}
import org.allenai.dictionary.ml.queryop.{RemoveEdge, RemoveToken, RemoveStar, SetToken}

class TestOpConjunction extends UnitSpec {

  val prefix2 = EvaluatedOp.fromPairs(
    SetToken(Prefix(2), QWord("p2")),
    List((1, 1), (3, 0), (4, 1), (9, 1))
  )
  val replace3 = EvaluatedOp.fromList(
    SetToken(QueryToken(3), QCluster("r3")),
    List(1, 2, 3, 4, 5)
  )
  val removeStar3 = EvaluatedOp.fromPairs(
    RemoveStar(3), List((1, 1), (3, 0))
  )

  val removeToken = EvaluatedOp.fromPairs(RemoveToken(1), List((1,1), (2,1), (5,0)))
  val removeLeft = EvaluatedOp.fromPairs(RemoveEdge(2, 1), List((1,0), (5,0)))

  "OpConjunction" should "calculate numEdits correctly" in {
    assertResult(OpConjunction(removeLeft))(None)
    var op = OpConjunction(replace3).get.add(prefix2)
    assertResult(List((1,1), (3,0), (4,1)))(op.numEdits.toSeq.sorted)

    assert(!op.canAdd(prefix2.op))
    assert(!op.canAdd(removeLeft.op))

    op = op.add(removeStar3)
    assertResult(List((1,1), (3,0)))(op.numEdits.toSeq.sorted)

    op = op.add(removeToken)
    op = op.add(removeLeft)
    assertResult(List((1,2)))(op.numEdits.toSeq.sorted)
    assert(!op.canAdd(SetToken(QueryToken(2), QWord(""))))

    assertResult(5)(op.size)
    assertResult(Set(prefix2.op, removeStar3.op, replace3.op,
      RemoveToken(1), RemoveToken(2)))(op.ops)
  }
}
