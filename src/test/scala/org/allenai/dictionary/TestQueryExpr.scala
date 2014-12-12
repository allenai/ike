package org.allenai.dictionary

import org.scalatest.FlatSpec
import scala.util.parsing.input.CharSequenceReader

class TestExpression extends FlatSpec {
  
  val withDict = Concat(
      DictToken("taskPhrase"),
      Capture(
          Concat(
              ClustToken("11"),
              ClustToken("101"))))
  
  "Expression" should "return tokens in order" in {
    val result = withDict.tokens
    val expected = DictToken("taskPhrase") :: ClustToken("11") :: ClustToken("101") :: Nil
    assert(result == expected)
  }
  
}