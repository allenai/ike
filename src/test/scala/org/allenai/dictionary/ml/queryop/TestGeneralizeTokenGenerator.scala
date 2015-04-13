package org.allenai.dictionary.ml.queryop

import org.allenai.common.testkit.UnitSpec
import org.allenai.dictionary.{QPos, QCluster, QWord, QueryLanguage}
import org.allenai.dictionary.ml._

import scala.collection.immutable.IntMap

class TestGeneralizeTokenGenerator extends UnitSpec {

  "GeneralizeTokenGenerator" should "Build correct tokens" in {
    val query = QueryLanguage.parse("a (?<x>b c) d").get
    val tokenized = TokenizedQuery.buildFromQuery(query)
    val generator = GeneralizingOpGenerator(true, true, Seq(2), true, tokenized.size)

    val matches = QueryMatches(QuerySlotData(
      Some(QWord("d")), QueryToken(4), false, false, true), Seq(
        QueryMatch(Seq(), true),
        QueryMatch(Seq(), false),
        QueryMatch(Seq(Token("a", "NN", "011")), true),
        QueryMatch(Seq(Token("a", "VB", "011")), false)
      )
    )

    val generated = generator.generate(matches)
    val remove = RemoveToken(4)
    val addWord = AddToken(QueryToken(4), QWord("a"))
    val setPos = SetToken(QueryToken(4), QPos("NN"))
    val setCluster = SetToken(QueryToken(4), QCluster("01"))

    // Note QPos(VB) is not suggested because it only occurs in non-matching sequences
    assertResult(IntMap(0 -> 0, 1 -> 1, 2 -> 0, 3 -> 1))(generated(remove))
    assertResult(IntMap(0 -> 0, 2 -> 0, 3 -> 1))(generated(addWord))
    assertResult(IntMap(2 -> 0))(generated(setPos))
    assertResult(IntMap(2 -> 0, 3 -> 1))(generated(setCluster))
  }

  it should "Should build correct multi remove ops" in {

    val matches = Seq(
      QueryMatches(QuerySlotData(Some(QWord("a")), QueryToken(1), false, true, false), Seq(
        QueryMatch(Seq(), true),
        QueryMatch(Seq(), false),
        QueryMatch(Seq(), false)
      )),
      QueryMatches(QuerySlotData(Some(QWord("b")), QueryToken(2), false, true, false), Seq(
        QueryMatch(Seq(), true),
        QueryMatch(Seq(), true),
        QueryMatch(Seq(Token("", "", "")), false)
      )),
      QueryMatches(QuerySlotData(Some(QWord("c")), QueryToken(3), false, true, false), Seq(
        QueryMatch(Seq(), true),
        QueryMatch(Seq(Token("", "", "")), false),
        QueryMatch(Seq(Token("", "", "")), false)
      )),
      QueryMatches(QuerySlotData(Some(QWord("c")), QueryToken(4), true, false, false), Seq(
        QueryMatch(Seq(), false),
        QueryMatch(Seq(Token("", "", "")), false),
        QueryMatch(Seq(Token("", "", "")), false)
      )),
      QueryMatches(QuerySlotData(Some(QWord("f")), QueryToken(6), false, false, true), Seq(
        QueryMatch(Seq(Token("", "", "")), false),
        QueryMatch(Seq(), false),
        QueryMatch(Seq(Token("", "", "")), true)
      )),
      QueryMatches(QuerySlotData(Some(QWord("e")), QueryToken(5), false, false, true), Seq(
        QueryMatch(Seq(Token("", "", "")), true),
        QueryMatch(Seq(), false),
        QueryMatch(Seq(), true)
      ))
    )

    val query = QueryLanguage.parse("a b c (?<x>d) e f").get
    val tokenized = TokenizedQuery.buildFromQuery(query)
    val generator = GeneralizingOpGenerator(true, true, Seq(2), true, tokenized.size)
    val generated = matches.map(generator.generate(_)).reduceLeft { (acc, next) => acc ++ next }
    assertResult(Set(
      RemoveToken(1), RemoveToken(6), RemoveEdge(2, 1), RemoveEdge(3, 1), RemoveEdge(5, 6)
    ))(generated.keySet)
    assertResult(IntMap(0 -> 0, 1 -> 1, 2 -> 1))(generated(RemoveToken(1)))
    assertResult(IntMap(0 -> 0, 1 -> 0, 2 -> 1))(generated(RemoveEdge(2, 1)))
    assertResult(IntMap(0 -> 0, 1 -> 1, 2 -> 1))(generated(RemoveEdge(3, 1)))
    assertResult(IntMap(0 -> 1, 1 -> 1, 2 -> 0))(generated(RemoveToken(6)))
    assertResult(IntMap(0 -> 0, 1 -> 1, 2 -> 0))(generated(RemoveEdge(5, 6)))
  }
}
