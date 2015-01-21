package org.allenai.dictionary

import org.allenai.common.testkit.ScratchDirectory
import org.allenai.common.testkit.UnitSpec
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser
import scala.collection.JavaConverters._

class TestSearcher extends UnitSpec with ScratchDirectory {
  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)
  def search(s: String): Iterator[BlackLabResult] = {
    val query = CorpusQueryLanguageParser.parse(s)
    val hits = searcher.find(query)
    BlackLabResult.fromHits(hits)
  }
  def groups(s: String): Iterator[String] = for {
    r <- search(s)
    (name, offset) <- r.captureGroups
    data = r.wordData.slice(offset.start, offset.end)
    words = data.map(_.word).mkString(" ")
    result = s"$name $words"
  } yield result
  "searcher" should "return the expected search results" in {
    val results = search(""" "I" [pos="VBP"] """)
    val resultStrings = results.map(r => r.matchWords.mkString(" ")).toSet
    assert(resultStrings == Set("I like", "I hate"))
  }
  it should "handle capture groups" in {
    val results = groups(""" "I" myGroup:[pos="VBP"] """)
    assert(results.toSet == Set("myGroup like", "myGroup hate"))
  }
  it should "handle multi-word capture groups" in {
    val results = groups(""" "I" myGroup:([pos="VBP"] [])""")
    assert(results.toSet == Set("myGroup like mango", "myGroup hate those"))
  }
  it should "handle multiple capture groups" in {
    val results = groups(""" "I" myGroup1:[pos="VBP"] myGroup2:[]""")
    val expected = Set("myGroup1 like", "myGroup1 hate", "myGroup2 mango", "myGroup2 those")
    assert(results.toSet == expected)
  }
  it should "handle repetition" in {
    val results = search(""" [pos="RB"]{0,1} [pos="JJ"] """)
    val resultStrings = results.map(r => r.matchWords.mkString(" ")).toSet
    assert(resultStrings == Set("great", "not great"))
  }
  it should "handle repitition and groupign" in {
    val results = groups(""" group1:([pos="RB"]{0,1} [pos="JJ"]) """)
    val expected = Set("group1 not great", "group1 great")
    assert(results.toSet == expected)
  }
}
