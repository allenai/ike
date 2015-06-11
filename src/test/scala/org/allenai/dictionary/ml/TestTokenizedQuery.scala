package org.allenai.dictionary.ml

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary._

class TestTokenizedQuery extends UnitSpec with ScratchDirectory {

  // Shorthands
  def qs(seq: QExpr*): QueryTokenSequence = TokenSequence(seq)
  def qsc(name: String, seq: Seq[QExpr], explicity: Boolean = true): QueryTokenSequence =
    CapturedTokenSequence(seq, name, explicity)

  "convertQuery" should "correctly tokenize" in {
    {
      val captureSeq = Seq(QWord(""), QDisj(Seq(QWord(""), QPos(""))))
      val query = QSeq(Seq(QWord("1"), QWord("2"), QNamed(QSeq(captureSeq), "col1")))
      val tokenized = TokenizedQuery.buildFromQuery(query, Seq())

      assertResult(qs(QWord("1"), QWord("2")))(tokenized.tokenSequences(0))
      assertResult(qsc("col1", captureSeq))(tokenized.tokenSequences(1))
      assertResult(query)(tokenized.getOriginalQuery)
    }
    {
      val query = QueryLanguage.parse("a NN+ (?<x> c) d*").get
      assertResult(query)(TokenizedQuery.buildFromQuery(query, Seq()).getOriginalQuery)
    }
    {
      val query = QueryLanguage.parse("a (?<y> {b, c} d) e f (g) (?<z> h)").get
      val tokenized = TokenizedQuery.buildFromQuery(query, Seq("y", "x", "z"))

      assertResult(qs(QWord("a")))(tokenized.tokenSequences(0))
      assertResult(qsc(
        "y",
        Seq(
          QDisj(List(QWord("b"), QWord("c"))),
          QWord("d")
        )
      ))(tokenized.tokenSequences(1))
      assertResult(qs(QWord("e"), QWord("f")))(tokenized.tokenSequences(2))
      assertResult(qsc("x", Seq(QWord("g")), false))(tokenized.tokenSequences(3))
      assertResult(qsc("z", Seq(QWord("h"))))(tokenized.tokenSequences(4))

      val seq = tokenized.getSeq
      val expectedSeq = Seq(
        QWord("a"),
        QDisj(List(QWord("b"), QWord("c"))),
        QWord("d"),
        QWord("e"),
        QWord("f"),
        QWord("g"),
        QWord("h")
      )
      assertResult(expectedSeq.size)(seq.size)
      seq.zip(expectedSeq).foreach {
        case (expected, actual) => assertResult(expected)(actual)
      }
    }
    {
      // Make sure if the user writes a overly-complex sequential query we can recover the original
      val query = QueryLanguage.parse("{a b} (?: {{c d}} {e}) (?<z> {f g})").get
      val tokenized = TokenizedQuery.buildFromQuery(query, Seq())

      val seq = tokenized.getSeq
      val expectedSeq = Seq(
        QWord("a"),
        QWord("b"),
        QWord("c"),
        QWord("d"),
        QWord("e"),
        QWord("f"),
        QWord("g")
      )
      assertResult(expectedSeq.size)(seq.size)
      seq.zip(expectedSeq).foreach {
        case (expected, actual) => assertResult(expected)(actual)
      }
    }
  }

  it should "get data correctly" in {
    val query = QueryLanguage.parse("a (?<c1> b c) d e (?<c2> f)").get
    val tokenized = TokenizedQuery.buildFromQuery(query, Seq())
    val expectedResults = Seq(
      QuerySlotData(Some(QWord("a")), QueryToken(1), false),
      QuerySlotData(Some(QWord("b")), QueryToken(2), true),
      QuerySlotData(Some(QWord("c")), QueryToken(3), true),
      QuerySlotData(Some(QWord("d")), QueryToken(4), false),
      QuerySlotData(Some(QWord("e")), QueryToken(5), false),
      QuerySlotData(Some(QWord("f")), QueryToken(6), true)
    )
    assertResult(expectedResults)(tokenized.getAnnotatedSeq)
  }

}
