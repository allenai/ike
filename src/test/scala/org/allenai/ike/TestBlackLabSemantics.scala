package org.allenai.ike

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.ike.index.TestData

class TestBlackLabSemantics extends UnitSpec with ScratchDirectory {
  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)
  def results(s: String): Iterator[BlackLabResult] = {
    val e = QExprParser.parse(s).get
    val q = BlackLabSemantics.blackLabQuery(e)
    val hits = searcher.find(q)
    BlackLabResult.fromHits(hits, "testCorpus")
  }
  def search(s: String): Set[String] = {
    for {
      result <- results(s)
      words = result.matchWords mkString (" ")
    } yield words
  }.toSet
  def searchGroups(s: String): Set[String] = {
    for {
      result <- results(s)
      (groupName, offsets) <- result.captureGroups
      data = result.wordData.slice(offsets.start, offsets.end)
      words = data map (_.word) mkString " "
      named = s"$groupName $words"
    } yield named
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

  it should "handle wildcard queries" in {
    assert(search("I .") == Set("I like", "I hate"))
  }

  it should "handle disjunctive queries" in {
    assert(search("like|hate") == Set("like", "hate"))
  }

  it should "handle repetition queries" in {
    assert(search("RB* JJ") == Set("great", "not great"))
    assert(search("RB+ JJ") == Set("not great"))
  }

  it should "handle groups" in {
    assert(searchGroups("I (?<x>VBP) DT* (?<y>NN|NNS)") ==
      Set("x like", "y mango", "x hate", "y bananas"))
    assert(searchGroups("I (?<x>.*) (?<y>NN|NNS)") ==
      Set("x like", "y mango", "x hate those", "y bananas"))
  }

  it should "handle multiple grouped wildcards" in {
    val q = "(?<x>{I, They, It} VBP) ."
    val expected = Set("x I like", "x I hate", "x They taste", "x It tastes")
    assert(searchGroups(q) == expected)
  }

  it should "handle all-wildcard queries" in {
    val q = "(?<x>.) . . ."
    val expected = Set("x I", "x hate", "x They", "x taste", "x It")
    assert(searchGroups(q) == expected)
  }

  it should "handle disjunctions at the beginning" in {
    val q = "{I, They} (?<x>VBP)"
    val expected = Set("x like", "x hate", "x taste")
    assert(searchGroups(q) == expected)
  }

}
