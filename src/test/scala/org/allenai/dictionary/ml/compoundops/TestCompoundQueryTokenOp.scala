package org.allenai.dictionary.ml.compoundops

import org.allenai.common.testkit.UnitSpec
import org.allenai.dictionary._
import org.allenai.dictionary.ml.TokenizedQuery
import org.allenai.dictionary.ml.primitveops._

import scala.collection.immutable.IntMap
import scala.language.implicitConversions

class TestCompoundQueryTokenOp extends UnitSpec {

  val prefix21 = SetToken(Prefix(2), QWord("p21"))
  val prefix22 = SetToken(Prefix(2), QWord("p22"))

  val prefix3 = SetToken(Prefix(3), QPos("NN"))
  val suffix1 = SetToken(Suffix(1), QWord("s1"))
  val suffix3 = SetToken(Suffix(3), QWord("s3"))
  val replace3 = SetToken(Match(3), QWord("r3"))
  val add21 = AddToken(2, QWord("a21"))
  val add22 = AddToken(2, QWord("a22"))
  val add3 = AddToken(3, QWord("a3"))

  "CompoundOp" should "Apply ops correctly" in {
    val ops = Seq(replace3, prefix22, prefix3, suffix1, suffix3)
    val startingQuery = QueryLanguage.parse("one (?<capture> c1 c2) two").get
    val startingTokenQuery = TokenizedQuery.buildFromQuery(startingQuery)
    val qExpr = CompoundQueryOp.applyOps(startingTokenQuery, ops).getQuery match {
      case QSeq(seq) => seq
      case _ => throw new RuntimeException()
    }

    assertResult(prefix3.qexpr)(qExpr(0))
    assertResult(prefix22.qexpr)(qExpr(1))
    assertResult(QWildcard())(qExpr(2))
    assertResult(QWord("one"))(qExpr(3))
    assertResult(QNamed(QSeq(Seq(QWord("c1"), replace3.qexpr)), "capture"))(qExpr(4))
    assertResult(QWord("two"))(qExpr(5))
    assertResult(suffix1.qexpr)(qExpr(6))
    assertResult(QWildcard())(qExpr(7))
    assertResult(suffix3.qexpr)(qExpr(8))
    assertResult(9)(qExpr.size)
  }

  "CompoundOp" should "apply AddToken ops" in {
    val startingQuery = QueryLanguage.parse("one (?<capture> c1 c2)").get
    val tokenized = TokenizedQuery.buildFromQuery(startingQuery)
    val ops = Seq(add22, add21)
    val modified = CompoundQueryOp.applyOps(tokenized, ops)
    assertResult(Set(add22.qexpr, add21.qexpr, tokenized.getSeq(1))) {
      modified.getSeq(1) match {
        case QDisj(seq) => seq.toSet
        case _ => throw new RuntimeException()
      }
    }
  }

  it should "apply disjunction of ops correctly" in {
    implicit def opToEvaluatedOp(x: TokenQueryOp): EvaluatedOp = EvaluatedOp(x, IntMap())
    val ops = Seq(suffix1, replace3, prefix21, prefix22, add3)
    val start = QueryLanguage.parse("(?<capture> c1 c2 c3) h").get
    val tokenized = TokenizedQuery.buildFromQuery(start)
    val modified = CompoundQueryOp.applyOps(tokenized, ops).getSeq
    assertResult(Set(prefix21.qexpr, prefix22.qexpr)) {
      modified(0) match {
        case QDisj(seq) => seq.toSet
        case _ => Set()
      }
    }
    assertResult(QWildcard())(modified(1))
    assertResult(QWord("c1"))(modified(2))
    assertResult(QWord("c2"))(modified(3))
    assertResult(Set(replace3.qexpr, add3.qexpr, QWord("c3"))) {
      modified(4) match {
        case QDisj(seq) => seq.toSet
        case _ => Set()
      }
    }
    assertResult(QWord("h"))(modified(5))
    assertResult(suffix1.qexpr)(modified(6))
    assertResult(7)(modified.size)
  }
}
