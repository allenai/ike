package org.allenai.dictionary

import org.scalatest.FlatSpec

class TestQueryExprSql extends FlatSpec {
  
  import QueryExpr._
  import SqlDatabase._
  val parse = QueryExprParser.parse _ andThen { x => x.get }
  val expr1 = parse("the ^1 (^0 ^1) algorithm")
  val expr2 = parse("(hello world) yo")
  val expr3 = parse("yo (hello world)")
  
  "SqlDatabase" should "get query predicates from expression" in {
    val expected = Seq(
        Equals(wordColumn(0), Some("the")),
        Prefix(clusterColumn(1), "1"),
        Prefix(clusterColumn(2), "0"),
        Prefix(clusterColumn(3), "1"),
        Equals(wordColumn(4), Some("algorithm")))
    val result = predicates(tokens(expr1))
    assert(result == expected)
  }
  
  it should "find captures in the middle" in {
    val expected = Seq(WordToken("the"), ClustToken("1"))
    val result = tokensBeforeCapture(expr1)
    assert(result == expected)
  }
  
  it should "find capture at begninning" in {
    val expected = Seq.empty[QToken]
    val result = tokensBeforeCapture(expr2)
    assert(result == expected)
  }
  
  it should "find capture at end" in {
    val expected = Seq(WordToken("yo"))
    val result = tokensBeforeCapture(expr3)
    assert(result == expected)
  }
  
  it should "create the correct select column names (middle)" in {
    val expected = Seq(wordColumn(2), wordColumn(3))
    val result = resultColumnNames(expr1)
    assert(result == expected)
  }
  
  it should "create the correct select column names (start)" in {
    val expected = Seq(wordColumn(0), wordColumn(1))
    val result = resultColumnNames(expr2)
    assert(result == expected)
  }
  
  it should "create the correct select column names (end)" in {
    val expected = Seq(wordColumn(1), wordColumn(2))
    val result = resultColumnNames(expr3)
    assert(result == expected)
  }

}