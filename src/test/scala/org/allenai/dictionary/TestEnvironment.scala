package org.allenai.dictionary

import org.scalatest.FlatSpec
import org.allenai.common.immutable.Interval

class TestEnvironment extends FlatSpec {
  val i = Interval.open _
  val parser = (s: String) => QueryExprParser.parse(s).get
  "Environment" should "correctly replace substrings 1" in {
    val s = "this is a test"
    val repls = ClusterReplacement(i(5, 7), "01") :: Nil
    val result = Environment.replaceClusters(s, repls)
    assert(result == "this ^01 a test")
  }
  
  it should "correctly replace substrings 2" in {
    val s = "this is a test"
    val repls = ClusterReplacement(i(5, 7), "01") :: ClusterReplacement(i(8, 9), "0"):: Nil
    val result = Environment.replaceClusters(s, repls)
    assert(result == "this ^01 ^0 test")
  }
  
  it should "correctly replace substrings 3" in {
    val s = "this is a test"
    val repls = ClusterReplacement(i(0, 4), "01") :: ClusterReplacement(i(10, 14), "0"):: Nil
    val result = Environment.replaceClusters(s, repls)
    assert(result == "^01 is a ^0")
  }
  
  it should "correctly replace substrings 4" in {
    val s = "this is a test"
    val repls = Nil
    val result = Environment.replaceClusters(s, repls)
    assert(result == "this is a test")
  }

  it should "correctly parse a query" in {
    val q = "$this (is a) test"
    val repls = ClusterReplacement(i(13, 17), "10") :: Nil
    val dicts = Map("this" -> Seq("some thing", "another"))
    val env = EnvironmentState(q, repls, dicts)
    val results = Environment.interpret(env, parser).map(QueryExpr.tokens)
    
    val wt = WordToken.apply _
    val ct = ClustToken.apply _
    val expected1 = wt("some") :: wt("thing") :: wt("is") :: wt("a") :: ct("10") :: Nil
    val expected2 = wt("another") :: wt("is") :: wt("a") :: ct("10") :: Nil
    val expected = expected1 :: expected2 :: Nil
    assert(expected == results)
  }
  
  it should "return expected word info" in {
    val q = "$this (is a) test"
    val info1 = WordTokenInfo("is", 1, i(7, 9))
    val info2 = WordTokenInfo("a", 2, i(10, 11))
    val info3 = WordTokenInfo("test", 3, i(13, 17))
    val info = info1 :: info2 :: info3 :: Nil
    val results = Environment.wordTokenInfo(q, parser)
    assert(results == info)
  }
}