package org.allenai.dictionary

import org.allenai.common.testkit.ScratchDirectory
import org.allenai.common.testkit.UnitSpec
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser
import scala.collection.JavaConverters._

class TestSearcher extends UnitSpec with ScratchDirectory {
  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)
  "searcher" should "return the expected search results" in {
    val query = CorpusQueryLanguageParser.parse(""" "I" [pos="VBP"] """)
    val hits = searcher.find(query)
    val results = BlackLabResult.fromHits(hits)
    val resultStrings = results.map(r => r.matchWords.mkString(" ")).toSet
    assert(resultStrings == Set("I like", "I hate"))
  }
}
