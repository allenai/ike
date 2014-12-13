package org.allenai.dictionary

import org.scalatest.FlatSpec

class TestDatabase extends FlatSpec {
  
  import Database.{wordColumn, clusterColumn}
  val parse = QueryExprParser.parse _ andThen { x => x.get }
  val expr1 = parse("the ^1 (^0 ^1) algorithm")
  val expr2 = parse("(hello world) yo")
  val expr3 = parse("yo (hello world)")
  
  "Database" should "get query constraints" in {
    val expected = Seq(
        Equals(wordColumn(0), "the"),
        Prefix(clusterColumn(1), "1"),
        Prefix(clusterColumn(2), "0"),
        Prefix(clusterColumn(3), "1"),
        Equals(wordColumn(4), "algorithm"))
    val tokens = expr1.tokens
    val result = Database.constraints(tokens)
    assert(result == expected)
  }
  
  it should "find captures in the middle" in {
    val expected = Seq(WordToken("the"), ClustToken("1"))
    val result = Database.tokensBeforeCapture(expr1)
    assert(result == expected)
  }
  
  it should "find capture at begninning" in {
    val expected = Seq.empty[Token]
    val result = Database.tokensBeforeCapture(expr2)
    assert(result == expected)
  }
  
  it should "find capture at end" in {
    val expected = Seq(WordToken("yo"))
    val result = Database.tokensBeforeCapture(expr3)
    assert(result == expected)
  }
  
  it should "create the correct select group (middle)" in {
    val expected = QuerySelect(Seq(wordColumn(2), wordColumn(3)))
    val result = Database.select(expr1)
    assert(result == expected)
  }
  
  it should "create the correct select group (start)" in {
    val expected = QuerySelect(Seq(wordColumn(0), wordColumn(1)))
    val result = Database.select(expr2)
    assert(result == expected)
  }
  
  it should "create the correct select group (end)" in {
    val expected = QuerySelect(Seq(wordColumn(1), wordColumn(2)))
    val result = Database.select(expr3)
    assert(result == expected)
  }
  
  println(Database.queryString(Database.query(expr1)))

}