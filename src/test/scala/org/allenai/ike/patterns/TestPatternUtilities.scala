package org.allenai.ike.patterns

import org.allenai.common.testkit.{ScratchDirectory, UnitSpec}

class TestPatternUtilities extends UnitSpec with ScratchDirectory {
  val namedPatterns = PatternUtilities.loadNamedPatterns("testPatterns.conf")

  "TestPatternUtilties" should "load up the correct test patterns" in {
    // sort to guarantee order on checks
    val namedPatternsSorted = namedPatterns.sortBy(_.name)
    assert(namedPatternsSorted.length == 2)
    assert(namedPatternsSorted.head.name == "result-percent")
    assert(namedPatternsSorted.head.pattern == "CD {%|percent|per cent|pct}")
    assert(namedPatternsSorted.last.name == "treatments")
    assert(namedPatternsSorted.last.pattern == "{were given|treated with|received|receiving} {CD|NN|JJ|IN}+")
  }

  it should "then create the correct searchRequest objects per name" in {
    val searcherMap = PatternUtilities.createSearchers(namedPatterns)

    assert(searcherMap.size == 2)

    assert(searcherMap.contains("result-percent"))
    val searcherRes = searcherMap("result-percent")
    assert(searcherRes.query.isLeft)
    assert(searcherRes.query.left.get == "CD {%|percent|per cent|pct}")

    assert(searcherMap.contains("treatments"))
    val searcherRes2 = searcherMap("treatments")
    assert(searcherRes2.query.isLeft)
    assert(searcherRes2.query.left.get == "{were given|treated with|received|receiving} {CD|NN|JJ|IN}+")

  }
}
