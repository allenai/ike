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
    val dict = Dictionary("this", Set("some thing", "another"), Set.empty)
    val env = EnvironmentState(q, repls, Seq(dict))
    val results = Environment.interpret(env, parser).map(QueryExpr.tokens)
    
    val wt = WordToken.apply _
    val ct = ClustToken.apply _
    val expected1 = wt("some") :: wt("thing") :: wt("is") :: wt("a") :: ct("10") :: Nil
    val expected2 = wt("another") :: wt("is") :: wt("a") :: ct("10") :: Nil
    val expected = expected1 :: expected2 :: Nil
    assert(expected == results)
  }
  
}