package org.allenai.dictionary.ml.queryop

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary._
import org.allenai.dictionary.ml._

import scala.collection.immutable.IntMap

class TestGeneralizingOpGenerator extends UnitSpec with ScratchDirectory {

  it should "Suggest correct POS operators" in {
    val query = QueryLanguage.parse("a (?<x>b c) d").get
    val tokenized = TokenizedQuery.buildFromQuery(query, Seq())
    var generator = GeneralizingOpGenerator(true, true)
    val slot = QueryToken(4)
    val setPosNN = SetToken(slot, QPos("NN"))
    val setPosCC = SetToken(slot, QPos("CC"))
    val setPosVB = SetToken(slot, QPos("VB"))

    val labels = IndexedSeq(Label.Negative, Label.Positive, Label.Negative, Label.Positive,
      Label.Positive, Label.Positive, Label.Positive, Label.Positive).zipWithIndex.
      map(x => WeightedExample(x._1, x._2, 0, 0, 1))

    def getWithGeneralization(gen: Generalization): Map[QueryOp, IntMap[Int]] = {
      val matches = QueryMatches(QuerySlotData(
        Some(QWord("d")), slot, true, Some(gen)
      ), Seq(
        QueryMatch(Seq(Token("a1", "CC")), false),
        QueryMatch(Seq(Token("d", "NN")), true),
        QueryMatch(Seq(Token("a4", "VB")), false),
        QueryMatch(Seq(Token("a1", "CC")), true),
        QueryMatch(Seq(Token("d", "MD")), true),
        QueryMatch(Seq(Token("a2", "MD")), false),
        QueryMatch(Seq(Token("c3", "MD")), false),
        QueryMatch(Seq(Token("a3", "MD")), false)
      ))
      generator.generate(matches, labels)
    }

    val m1 = getWithGeneralization(GeneralizeToDisj(Seq("CC", "NN", "VB").map(QPos), Seq(), true))
    assertResult(IntMap(1 -> 0))(m1(setPosNN))
    assertResult(IntMap(0 -> 1, 3 -> 0))(m1(setPosCC))
    assertResult(IntMap(2 -> 1))(m1(setPosVB))
    assertResult(3)(m1.size)

    val m2 = getWithGeneralization(GeneralizeToDisj(Seq(QPos("NN")), Seq(), true))
    assertResult(IntMap(1 -> 0))(m2(setPosNN))
    assertResult(1)(m2.size)

    val simPhrases = QSimilarPhrases(Seq(QWord("d")), 4,
      Seq(("a1", 0.9), ("a2", 0.8), ("a3", 0.7), ("a4", 0.6)).
        map(x => SimilarPhrase(Seq(QWord(x._1)), x._2)))
    val m3 = getWithGeneralization(GeneralizeToDisj(Seq(), Seq(simPhrases), true))

    // Match a1
    assertResult(IntMap(0 -> 1, 1 -> 0, 3 -> 0, 4 -> 0))(
      m3(SetToken(slot, simPhrases.copy(pos = 1)))
    )
    // match a1,a2,a3
    assertResult(IntMap(0 -> 1, 1 -> 0, 3 -> 0, 4 -> 0, 5 -> 1, 7 -> 1))(
      m3(SetToken(slot, simPhrases.copy(pos = 3)))
    )
    // match a1,a2,a3,a4
    assertResult(IntMap(0 -> 1, 1 -> 0, 3 -> 0, 4 -> 0, 5 -> 1, 7 -> 1, 2 -> 1))(
      m3(SetToken(slot, simPhrases.copy(pos = 4)))
    )

    // Note there will not be an op that matches a1,a2 because a2 and a3 add elements to the
    // possible hits that have the same label so they should be merged together
    assertResult(3)(m3.size)

    generator = GeneralizingOpGenerator(true, true, 4)
    val m4 = getWithGeneralization(GeneralizeToDisj(Seq(), Seq(simPhrases), true))
    assertResult(IntMap(0 -> 1, 1 -> 0, 3 -> 0, 4 -> 0, 5 -> 1, 7 -> 1))(
      m4(SetToken(slot, simPhrases.copy(pos = 3)))
    )
    assertResult(1)(m4.size)
  }
}
