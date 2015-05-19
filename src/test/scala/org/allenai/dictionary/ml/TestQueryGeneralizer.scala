package org.allenai.dictionary.ml

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary._
import org.allenai.dictionary.index.TestData

class TestQueryGeneralizer extends UnitSpec with ScratchDirectory {

  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)
  val searchers = Seq(searcher)

  it should "cover all PosTags" in {
    val tagSet = QueryLanguage.parser.posTagSet.toSet
    val generalizingTagset = QueryGeneralizer.posSets.reduce((a, b) => a ++ b)
    assert((tagSet == generalizingTagset))
  }

  it should "suggest correct generalizations" in {
    {
      val gens = QueryGeneralizer.queryGeneralizations(QPos("NN"), (searchers), 10)
      val qexprs = gens.asInstanceOf[GeneralizeToDisj].elements
      assert(qexprs.contains(QPos("NNS")))
      assert(!qexprs.contains(QPos("VBG")))
      assert(!qexprs.contains(QPos("NN")))
    }
    {
      val gens = QueryGeneralizer.queryGeneralizations(
        QDisj(Seq(QPos("NN"), QPos("VBG"))), searchers, 10
      )
      val qexprs = gens.asInstanceOf[GeneralizeToDisj].elements
      assert(qexprs.contains(QPos("NNS")))
      assert(qexprs.contains(QPos("VB")))
      assert(!qexprs.contains(QPos("JJ")))
      assert(!qexprs.contains(QPos("JJS")))
      assert(!qexprs.contains(QPos("VBG")))
    }
    {
      val gens = QueryGeneralizer.queryGeneralizations(QWord("I"), searchers, 10)
      assert(gens == GeneralizeToDisj(Seq(QPos("PRP"))))
    }
    {
      val gens = QueryGeneralizer.queryGeneralizations(QRepetition(QPos("NN"), 1, 4), searchers, 10)
      val qexprs = gens.asInstanceOf[GeneralizeToDisj].elements

      assert(qexprs.size == 4)
      assert(qexprs contains QRepetition(QPos("NNS"), 1, 4))
      assert(qexprs contains QRepetition(QPos("FW"), 1, 4))
      assert(qexprs contains QRepetition(QPos("NNP"), 1, 4))
      assert(qexprs contains QRepetition(QPos("NNPS"), 1, 4))
    }
    assertResult(GeneralizeToNone())(QueryGeneralizer.queryGeneralizations(
      QRepetition(QWildcard(), 1, 4), searchers, 10
    ))
  }
}
