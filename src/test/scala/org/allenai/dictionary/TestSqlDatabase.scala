package org.allenai.dictionary

import org.scalatest.FlatSpec
import java.io.File

class TestSqlDatabase extends FlatSpec {
  
  import SqlDatabase._
  val dbFile = File.createTempFile("database", "db")
  val dbPath = dbFile.getAbsolutePath
  val n = 3
  val w1 = WordWithCluster("cats", "1")
  val w2 = WordWithCluster("like", "0")
  val w3 = WordWithCluster("dogs", "1")
  val w4 = WordWithCluster("purr", "0")
  val gram1 = CountedNGram(Seq(w1, w2, w3), 1)
  val gram2 = CountedNGram(Seq(w3, w2, w1), 1)
  val gram3 = CountedNGram(Seq(w1, w4), 1)
  val grams = Seq(gram1, gram2, gram3)
  
  "DatabaseOperations" should "create and delete" in {
    val db = SqlDatabase(dbPath, n)
    db.create
    assert(dbFile.exists)
    db.delete
    db.create
    db.delete
  }
  
  it should "insert and query" in {
    val db = SqlDatabase(dbPath, n)
    db.create
    db.insert(grams)
    val select = Seq(wordColumn(0), wordColumn(1))
    val where = Seq(Equals(wordColumn(2), Some(w3.word)))
    val query = SqlQuery(select, where)
    val results = db.select(query)
    val expected = Seq(QueryResult(s"${w1.word} ${w2.word}", 1))
    assert(expected == results)
    db.delete
  }
  
  it should "handle query expressions" in {
    val expr = Concat(Capture(WordToken(w1.word)), ClustToken(w4.cluster))
    val db = SqlDatabase(dbPath, n)
    db.create
    db.insert(grams)
    val results = db.query(expr)
    val expected = Seq(QueryResult(s"${w1.word}", 1))
    assert(expected == results)
    db.delete
  }

}