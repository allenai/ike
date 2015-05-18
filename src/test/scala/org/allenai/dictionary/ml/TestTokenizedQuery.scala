package org.allenai.dictionary.ml

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary._

class TestTokenizedQuery extends UnitSpec with ScratchDirectory {

  "convertQuery" should "correctly tokenize" in {
    {
      val captureSeq = Seq(QWord(""), QDisj(Seq(QWord(""), QPos(""))))
      val query = QSeq(Seq(QWord("1"), QWord("2"), QNamed(QSeq(captureSeq), "col1")))
      val tokenized = TokenizedQuery.buildFromQuery(query)

      assertResult(Seq(QWord("1"), QWord("2")))(tokenized.nonCaptures(0))
      assertResult(CaptureSequence(captureSeq, "col1"))(tokenized.captures(0))
      assertResult(query)(tokenized.getQuery)
    }
    {
      val query = QueryLanguage.parse("a (?<y> {b, c} d) e f (?<x> g) (?<z> h)").get
      val tokenized = TokenizedQuery.buildFromQuery(query)

      assertResult(CaptureSequence(
        Seq(
          QDisj(List(QWord("b"), QWord("c"))),
          QWord("d")
        ),
        "y"
      ))(tokenized.captures(0))
      assertResult(CaptureSequence(Seq(QWord("g")), "x"))(tokenized.captures(1))
      assertResult(CaptureSequence(Seq(QWord("h")), "z"))(tokenized.captures(2))

      assertResult(Seq(QWord("a")))(tokenized.nonCaptures(0))
      assertResult(Seq(QWord("e"), QWord("f")))(tokenized.nonCaptures(1))
      assertResult(Seq())(tokenized.nonCaptures(2))
      assertResult(Seq())(tokenized.nonCaptures(3))
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
  }

  it should "test exception" in {
    intercept[UnconvertibleQuery] {
      // QStar so variable length
      val q = QSeq(Seq(QStar(QWildcard()), QNamed(QPos(""), "c1")))
      TokenizedQuery.buildFromQuery(q)
    }
    intercept[UnconvertibleQuery] {
      // Disjunction with different sized clauses
      val nontokenizableSeq = QDisj(Seq(QSeq(Seq(
        QPos(""),
        QPos("")
      )), QWord("")))
      val q = QNamed(nontokenizableSeq, "c1")
      TokenizedQuery.buildFromQuery(q)
    }
  }
}
