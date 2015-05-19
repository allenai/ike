package org.allenai.dictionary.ml.queryop

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary.{ QPos, QWord, QueryLanguage }
import org.allenai.dictionary.ml._

import scala.collection.immutable.IntMap

class TestGeneralizeTokenGenerator extends UnitSpec with ScratchDirectory {

  "GeneralizeTokenGenerator" should "Suggest correct operators" in {
    val query = QueryLanguage.parse("a (?<x>b c) d").get
    val tokenized = TokenizedQuery.buildFromQuery(query)
    val generator = GeneralizingOpGenerator(true, true, false)

    val setPosNN = SetToken(QueryToken(4), QPos("NN"))
    val setPosCC = SetToken(QueryToken(4), QPos("CC"))
    val setPosVB = SetToken(QueryToken(4), QPos("VB"))

    def getWithGeneralization(gen: Generalization): Map[QueryOp, IntMap[Int]] = {
      val matches = QueryMatches(QuerySlotData(
        Some(QWord("d")), QueryToken(4), false, false, true, Some(gen)
      ), Seq(
        QueryMatch(Seq(), true),
        QueryMatch(Seq(Token("a", "CC")), false),
        QueryMatch(Seq(Token("a", "NN")), true),
        QueryMatch(Seq(Token("a", "VB")), false),
        QueryMatch(Seq(Token("a", "CC")), true)
      ))
      generator.generate(matches)
    }

    //    val m1 = getWithGeneralization(GeneralizeToAny(1, 1))
    //    assertResult(IntMap(2 -> 0))(m1(setPosNN))
    //    assertResult(IntMap(1 -> 1, 4 -> 0))(m1(setPosCC))
    //    assertResult(IntMap(3 -> 1))(m1(setPosVB))
    //    assertResult(3)(m1.size)

    val m2 = getWithGeneralization(GeneralizeToDisj(Seq(QPos("NN"))))
    assertResult(IntMap(2 -> 0))(m2(setPosNN))
    assertResult(1)(m2.size)
  }
}
