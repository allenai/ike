package org.allenai.dictionary

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }

class TestQExprParser extends UnitSpec with ScratchDirectory {

  val wc = QWildcard()
  // A bunch of QExpr shorthand functions
  // scalastyle:off
  def w(s: String) = QWord(s)
  def p(s: String) = QPos(s)
  def qs(exprs: QExpr*) = QSeq(exprs)
  def cap(expr: QExpr) = QUnnamed(expr)
  def cap(name: String, expr: QExpr) = QNamed(expr, name)
  def nocap(expr: QExpr) = QNonCap(expr)
  def or(exprs: QExpr*) = QDisj(exprs)
  def star(expr: QExpr) = QStar(expr)
  def rep(expr: QExpr, min: Int, max: Int) = QRepetition(expr, min, max)
  def g(words: Seq[String], pos: Int) = QGeneralizePhrase(words.map(QWord), pos)
  // scalastyle:on

  def parse(s: String): QExpr = QExprParser.parse(s).get

  "QExprParser" should "parse correctly" in {

    val q1 = "this is a test"
    val e1 = qs(w("this"), w("is"), w("a"), w("test"))
    assert(parse(q1) == e1)

    val q2 = "this is DT NN"
    val e2 = qs(w("this"), w("is"), p("DT"), p("NN"))
    assert(parse(q2) == e2)

    val q4 = "this is (a test)"
    val e4 = qs(w("this"), w("is"), cap(qs(w("a"), w("test"))))
    assert(parse(q4) == e4)

    val q5 = "this is (?<foo> a test)"
    val e5 = qs(w("this"), w("is"), cap("foo", qs(w("a"), w("test"))))
    assert(parse(q5) == e5)

    val q6 = "this is (?:a test)"
    val e6 = qs(w("this"), w("is"), nocap(qs(w("a"), w("test"))))
    assert(parse(q6) == e6)

    val q7 = "(?:this|that) is a test"
    val e7 = qs(nocap(or(w("this"), w("that"))), w("is"), w("a"), w("test"))
    assert(parse(q7) == e7)

    val q8 = ". is a test"
    val e8 = qs(wc, w("is"), w("a"), w("test"))
    assert(parse(q8) == e8)

    val q9 = ".* is a test"
    val e9 = qs(star(wc), w("is"), w("a"), w("test"))
    assert(parse(q9) == e9)

    val q10 = "{this, that} is a test"
    val e10 = qs(or(w("this"), w("that")), w("is"), w("a"), w("test"))
    assert(parse(q10) == e10)

    val q11 = "the thing[1,5] ran"
    val e11 = qs(w("the"), rep(w("thing"), 1, 5), w("ran"))
    assert(parse(q11) == e11)

    val q12 = "the {thing [1,-1], ran}"
    val e12 = qs(w("the"), or(rep(w("thing"), 1, -1), w("ran")))
    assert(parse(q12) == e12)

    val q13 = "the \"fat cat\"~10 \"ran\"~1 {fast,quick~34}"
    val e13 = qs(w("the"), g(Seq("fat", "cat"), 10), g(Seq("ran"), 1),
      or(w("fast"), g(Seq("quick"), 34)))
    assert(parse(q13) == e13)

    val q14 = "the \"fat cat\"[1,2]"
    val e14 = qs(w("the"), rep(g(Seq("fat", "cat"), 0), 1, 2))
    assert(parse(q14) == e14)
  }
}
