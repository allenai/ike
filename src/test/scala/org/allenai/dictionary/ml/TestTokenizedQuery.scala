package org.allenai.dictionary.ml

import org.allenai.common.testkit.{ScratchDirectory, UnitSpec}
import org.allenai.dictionary._

class TestTokenizedQuery extends UnitSpec with ScratchDirectory {

  "convertQuery" should "correctly tokenize" in {
    val captureSeq = Seq(QWord(""), QDisj(Seq(QCluster(""), QPos(""))))
    val query = QSeq(Seq(QWord(""), QUnnamed(QSeq(captureSeq)), QCluster("")))
    val tokenized =  TokenizedQuery.buildFromQuery(query)
    assertResult(captureSeq) {tokenized.capture}
    assertResult(Seq(QWord(""))) {tokenized.left}
    assertResult(Seq(QCluster(""))) {tokenized.right}

    val captureSeq2 = Seq(QDisj(Seq(QCluster(""), QPos(""))), QPos(""), QCluster(""))
    val query2 = QSeq(Seq(QUnnamed(QSeq(captureSeq2)),
      QDisj(Seq(QPos(""), QCluster("")))))
    val tokenized2 = TokenizedQuery.buildFromQuery(query2)
    assertResult(captureSeq2) {tokenized2.capture}
    assertResult(Seq()) {tokenized2.left}
    assertResult(Seq(QDisj(Seq(QPos(""), QCluster(""))))) {tokenized2.right}
  }

  it should "test exception" in {
    intercept[UnconvertibleQuery] {TokenizedQuery.buildFromQuery(QWord("")) }
    intercept[UnconvertibleQuery] {
      // Two captures
      val q = QSeq(Seq(QUnnamed(QCluster("")), QNamed(QPos(""), "")))
      TokenizedQuery.buildFromQuery(q)
    }
    intercept[UnconvertibleQuery] {
      // QStar so variable length
      val q = QSeq(Seq(QStar(QWildcard()), QNamed(QPos(""), "")))
      TokenizedQuery.buildFromQuery(q)
    }
    intercept[UnconvertibleQuery] {
      // Disjunction with different sized clauses
      val nontokenizableSeq = QDisj(Seq(QSeq(Seq(QPos(""),
        QPos(""))), QWord("")))
      val q = QUnnamed(nontokenizableSeq)
      TokenizedQuery.buildFromQuery(q)
    }
  }
}