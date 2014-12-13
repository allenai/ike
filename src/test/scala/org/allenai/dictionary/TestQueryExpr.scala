package org.allenai.dictionary

import org.scalatest.FlatSpec
import scala.util.parsing.input.CharSequenceReader

class TestQueryExpr extends FlatSpec {
  
  val withDict = Concat(
      DictToken("taskPhrase"),
      Capture(
          Concat(
              ClustToken("11"),
              WordToken("hello"))))
  
  "QueryExpr" should "return tokens in order" in {
    val result = withDict.tokens
    val expected = DictToken("taskPhrase") :: ClustToken("11") :: WordToken("hello") :: Nil
    assert(result == expected)
  }
  
  it should "correctly parse string" in {
    val result = QueryExprParser.parse("$taskPhrase (^11 hello)").get
    assert(result == withDict)
  }
  
}