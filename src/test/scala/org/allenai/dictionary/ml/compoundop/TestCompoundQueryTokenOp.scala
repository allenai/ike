package org.allenai.dictionary.ml.compoundop

import org.allenai.common.testkit.UnitSpec
import org.allenai.dictionary._
import org.allenai.dictionary.ml._
import org.allenai.dictionary.ml.queryop._

import scala.collection.immutable.IntMap
import scala.language.implicitConversions

class TestCompoundQueryTokenOp extends UnitSpec {

  val prefix21 = SetToken(Prefix(2), QWord("p21"))
  val prefix22 = SetToken(Prefix(2), QWord("p22"))

  val prefix3 = SetToken(Prefix(3), QPos("NN"))
  val suffix1 = SetToken(Suffix(1), QWord("s1"))
  val suffix3 = SetToken(Suffix(3), QWord("s3"))
  val replace3 = SetToken(QueryToken(3), QCluster("11"))
  val add1 = AddToken(1, QWord("a3"))
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

  it should "apply AddToken ops" in {
    val startingQuery = QueryLanguage.parse("{one, two} (?<capture> c1 c2)").get
    val tokenized = TokenizedQuery.buildFromQuery(startingQuery)
    val ops = Seq(add22, add21, add1)
    val modified = CompoundQueryOp.applyOps(tokenized, ops).getSeq
    val expected = QueryLanguage.parse("{one, two, a3} (?<capture> {c1, a21, a22} c2)").get
    assertResult(Set(QWord("one"), QWord("two"), add1.qexpr))(
      modified(0).asInstanceOf[QDisj].qexprs.toSet)
    assertResult(Set(QWord("c1"), add21.qexpr, add22.qexpr))(
      modified(1).asInstanceOf[QDisj].qexprs.toSet)
  }

  "CompoundOp" should "apply remove ops" in {
    val startingQuery = QueryLanguage.parse("p1 p2 (?<capture> c1) s1 s2").get
    val tokenized = TokenizedQuery.buildFromQuery(startingQuery)
    assertResult(QSeq(Seq(QNamed(QWord("c1"), "capture"), QWord("s1")))) {
      CompoundQueryOp.applyOps(tokenized, Set(RemoveToken(1),
        RemoveToken(2), RemoveToken(5))).getQuery
    }
  }

  "CompoundOp" should "work for repetitions" in {
    val startingQuery = QueryLanguage.parse("NN* (?<capture> c1)").get
    val tokenized = TokenizedQuery.buildFromQuery(startingQuery)
    val min1 = SetMin(1, 1)
    val max1 = SetMax(1, 1)
    val set = SetToken(QueryToken(1), QPos("VB"))
    assertResult(QueryLanguage.parse("NN (?<capture> c1)").get) {
      CompoundQueryOp.applyOps(tokenized, Set(min1, max1)).getQuery
    }
    assertResult(QueryLanguage.parse("VB* (?<capture> c1)").get) {
      CompoundQueryOp.applyOps(tokenized, Set(set)).getQuery
    }
    assertResult(QueryLanguage.parse("VB (?<capture> c1)").get) {
      CompoundQueryOp.applyOps(tokenized, Set(min1, max1, set)).getQuery
    }
    assertResult(QueryLanguage.parse("VB+ (?<capture> c1)").get) {
      CompoundQueryOp.applyOps(tokenized, Set(min1, set)).getQuery
    }
  }

  "CompoundOp" should "work for set repetition token" in {
    val startingQuery = QueryLanguage.parse("NN* NNS[2,4] (?<capture> c1)").get
    val tokenized = TokenizedQuery.buildFromQuery(startingQuery)
    val max1 = SetMax(1, 5)
    val set10 = SetRepeatedToken(1, 1, QWord("the"))
    val set13 = SetRepeatedToken(1, 3, QWord("cat"))
    val set22 = SetRepeatedToken(2, 2, QWord("cat"))
    val set24 = SetRepeatedToken(2, 4, QWord("cat"))
    val set23 = SetRepeatedToken(2, 3, QWord("cat"))

    assertResult(QueryLanguage.parse("the NN* NNS[2,4] (?<capture> c1)").get) {
      CompoundQueryOp.applyOps(tokenized, Set(set10)).getQuery
    }
    assertResult(QueryLanguage.parse("the NN cat NN* NNS[2,4] (?<capture> c1)").get) {
      CompoundQueryOp.applyOps(tokenized, Set(set10, set13)).getQuery
    }
    assertResult(QueryLanguage.parse("the NN cat NN[0,2] NNS[2,4] (?<capture> c1)").get) {
      CompoundQueryOp.applyOps(tokenized, Set(set10, set13, max1)).getQuery
    }
    assertResult(QueryLanguage.parse("NN* NNS cat NNS[0,2] (?<capture> c1)").get) {
      CompoundQueryOp.applyOps(tokenized, Set(set22)).getQuery
    }
    assertResult(QueryLanguage.parse("NN* NNS[3,3] cat (?<capture> c1)").get) {
      CompoundQueryOp.applyOps(tokenized, Set(set24)).getQuery
    }
    assertResult(QueryLanguage.parse("NN* NNS cat cat NNS[0,1] (?<capture> c1)").get) {
      CompoundQueryOp.applyOps(tokenized, Set(set22, set23)).getQuery
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
