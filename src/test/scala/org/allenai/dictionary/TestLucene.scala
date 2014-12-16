package org.allenai.dictionary

import org.scalatest.FlatSpec
import java.nio.file.Files

class TestLucene extends FlatSpec {
      
  val docs = "This is some super TEXT that was super slick." :: "This is super salty!" :: Nil
  val wordClusters = Map("super" -> "00", "slick" -> "01", "salty" -> "001", "text" -> "1")
    
  val tempDir = Files.createTempDirectory("lucene").toFile
  val writer = LuceneWriter(tempDir, wordClusters)
  writer.write(docs.iterator)
  val reader = LuceneReader(tempDir)
  
  "Lucene" should "search over words and clusters" in {
    val x = reader.wordQuery("super")
	val y = reader.clusterPrefixQuery("0")
	val q = reader.sequenceQuery(x :: y :: Nil)
	val results = reader.matches(q).map(_.mkString(" ")).toSet
	assert(results == Set("super slick", "super salty"))
  }
  
  it should "handle regexes" in {
    val x = reader.wordQuery("super")
	val y = reader.clusterPrefixQuery("")
	val q = reader.sequenceQuery(x :: y :: Nil)
	val results = reader.matches(q).map(_.mkString(" ")).toSet
	assert(results == Set("super slick", "super salty", "super text"))
  }
  
  it should "interpret queries" in {
    val queryString = "super (^0)"
    val queryExpr = QueryExprParser.parse(queryString).get
    val hits = reader.execute(queryExpr)
    val expected = LuceneHit("slick", 1) :: LuceneHit("salty", 1) :: Nil
    assert(hits.toSet == expected.toSet)
  }
  
}