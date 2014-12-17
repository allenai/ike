package org.allenai.dictionary

import org.scalatest.FlatSpec
import scala.util.parsing.input.CharSequenceReader

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
  
}