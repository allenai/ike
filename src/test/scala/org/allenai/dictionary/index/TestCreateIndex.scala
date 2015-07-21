package org.allenai.dictionary.index

import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser
import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }

import scala.collection.JavaConverters._

class TestCreateIndex extends UnitSpec with ScratchDirectory {
  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)
  "createTestIndex" should "create the index" in {
    val reader = searcher.getIndexReader
    assert(reader.numDocs == TestData.indexableTexts.size)
  }
  it should "add the doc content" in {
    val i = CorpusQueryLanguageParser.parse(""" "I" [pos="VBP"] """)
    val hits = searcher.find(i)
    assert(hits.numberOfDocs == 2)
    assert(hits.iterator.asScala.toList.size == 2)
  }
}
