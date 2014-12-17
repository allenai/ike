package org.allenai.dictionary

import org.scalatest.FlatSpec
import java.nio.file.Files

class TestFullExample extends FlatSpec {
  val docs =
    Seq("Dogs love cats, and cats hate dogs.", "Cats are made from fur.", "Dogs are made of fur.",
        "Cats drink milk.")
    
  val clusters = Map("dogs" -> "00", "cats" -> "00", "hate" -> "10", "drink" -> "10",
      "milk" -> "01", "of" -> "11", "fur" -> "11", "love" -> "10")
  
  val tempDir = Files.createTempDirectory("lucene").toFile
  val writer = LuceneWriter(tempDir, clusters)
  writer.write(docs.iterator)
  val reader = LuceneReader(tempDir)
  val parser = (s: String) => QueryExprParser.parse(s).get
  
  val dict = Map("verb" -> Seq("love", "hate", "drink"))
  
  "FullExample" should "return expected results 1" in {
    
    val queryString = "^0 ($verb ^0)"
    val env = EnvironmentState(queryString, Nil, dict)
    val queries = Environment.interpret(env, parser)
    val results = reader.execute(queries)
    val expected = Seq(LuceneHit("love cats", 1), LuceneHit("hate dogs", 1),
        LuceneHit("drink milk", 1))
    assert(results.toSet == expected.toSet)
    
  }
  
  it should "return expected results 2" in {
    val queryString = "dogs ($verb) ^0"
    val dogsTokenInfo = Environment.wordTokenInfo(queryString, parser).filter(_.word == "dogs").head
    val repl = ClusterReplacement(dogsTokenInfo.offset, "0")
    val env = EnvironmentState(queryString, repl :: Nil, dict)
    val queries = Environment.interpret(env, parser)
    val results = reader.execute(queries)
    val expected = Seq(LuceneHit("love", 1), LuceneHit("hate", 1), LuceneHit("drink", 1))
    assert(results.toSet == expected.toSet)
  }
    
}