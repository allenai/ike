package org.allenai.dictionary

import org.allenai.common.testkit.ScratchDirectory
import org.allenai.common.testkit.UnitSpec

class TestBlackLabSemantics extends UnitSpec with ScratchDirectory {
  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)
  val semantics = BlackLabSemantics(searcher)
  def search(s: String) = {
    val e = QExprParser.parse(s).get
    val q = semantics.blackLabQuery(e)
    val hits = searcher.find(q)
    val results = BlackLabResult.fromHits(hits)
    for {
      result <- results
      words = result.matchWords mkString(" ")
    } yield words
  }.toSet
  
  "BlackLabSemantics" should "handle single word queries" in {
    assert(search("like") == Set("like"))
    assert(search("garbage") == Set.empty)
  }
  
  it should "handle pos tags" in {
    assert(search("NNS") == Set("bananas"))
    assert(search("PRP") == Set("I", "It", "They"))
  }
  
  it should "handle multi-term queries" in {
    assert(search("I VBP") == Set("I like", "I hate"))
    assert(search("PRP VBP") == Set("I like", "I hate", "It tastes", "They taste"))
  }
  
  it should "handle cluster prefix queries" in {
    assert(search("^0") == Set("I", "mango", "It", "bananas", "They"))
    assert(search("^00") == Set("mango", "bananas"))
  }
  
  it should "handle wildcard queries" in {
    assert(search("I .") == Set("I like", "I hate"))
  }
  
  it should "handle disjunctive queries" in {
    assert(search("like|hate") == Set("like", "hate"))
  }
  
}