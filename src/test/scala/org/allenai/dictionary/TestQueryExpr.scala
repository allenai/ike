package org.allenai.dictionary

import org.scalatest.FlatSpec
import scala.util.parsing.input.CharSequenceReader
import org.allenai.common.immutable.Interval

class TestQueryExpr extends FlatSpec {
  
  import QueryExpr.tokens
  
  val hello = WordToken("hello")
  val world = WordToken("world")
  val hola = WordToken("hola")
  val mundo = WordToken("mundo")
  val goodnight = WordToken("goodnight")
  val moon = WordToken("moon")
  val later = WordToken("later")
  val clust11 = ClustToken("11")
  val dictRef1 = DictToken("dict1")
  val dictRef2 = DictToken("dict2")
  
  val withOneDict = Concat(dictRef1, Capture(Concat(clust11, hello)))
  val withTwoDicts = Concat(dictRef1, Capture(Concat(clust11, hello, dictRef2)))
              
  val dictReplacement1 = Seq(Concat(hello, world), Concat(hola, mundo))
  val dictReplacement2 = Seq(Concat(goodnight, moon), Concat(later))
  val dicts = Map(dictRef1.value -> dictReplacement1, dictRef2.value -> dictReplacement2)
  
  "QueryExpr" should "return tokens in order" in {
    val result = tokens(withOneDict)
    val expected = dictRef1 :: clust11 :: hello :: Nil
    assert(result == expected)
  }
  
  it should "correctly parse string" in {
    val result = QueryExprParser.parse("$dict1 (^11 hello)").get
    assert(result == withOneDict)
  }
  
  it should "replace one dictionary" in {
    val expected1 = hello :: world :: clust11 :: hello :: Nil
    val expected2 = hola :: mundo :: clust11 :: hello :: Nil
    val expected = expected1 :: expected2 :: Nil
    val got = QueryExpr.evalDicts(withOneDict, dicts).map(tokens)
    assert(got == expected)
  }
  
  it should "replace two dictionaries" in {
    val expected1 = hello :: world :: clust11 :: hello :: goodnight :: moon :: Nil
    val expected2 = hello :: world :: clust11 :: hello :: later :: Nil
    val expected3 = hola :: mundo :: clust11 :: hello :: goodnight :: moon :: Nil
    val expected4 = hola :: mundo :: clust11 :: hello :: later :: Nil
    val expected = expected1 :: expected2 :: expected3 :: expected4 :: Nil
    val got = QueryExpr.evalDicts(withTwoDicts, dicts).map(tokens)
    assert(got == expected)
  }
  
  it should "find token offsets" in {
    val i = Interval.open _
    val input = "hello  (this is ) a ^0 test"
    val parsedTokens = tokens(QueryExprParser.parse(input).get)
    val offsets = QueryExpr.tokenOffsets(input, parsedTokens)
    val expected = i(0, 5) :: i(8, 12) :: i(13, 15) :: i(18, 19) :: i(21, 22) :: i(23, 27) :: Nil
    assert(expected == offsets)
  }
  
  it should "return expected word info" in {
    val i = Interval.open _
    val parser = (s: String) => QueryExprParser.parse(s).get
    val q = "$this (is a) test"
    val pos1 = TokenPosition(0, i(1, 5))
    val pos2 = TokenPosition(1, i(7, 9))
    val pos3 = TokenPosition(2, i(10, 11))
    val pos4 = TokenPosition(3, i(13, 17))
    val info = pos1 :: pos2 :: pos3 :: pos4 :: Nil
    val expr = parser(q)
    val results = QueryExpr.tokenPositions(q, expr)
    assert(results == info)
  }
  
}