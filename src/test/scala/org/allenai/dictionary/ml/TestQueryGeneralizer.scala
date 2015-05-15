package org.allenai.dictionary.ml

import org.allenai.common.testkit.{ScratchDirectory, UnitSpec}
import org.allenai.dictionary._
import org.allenai.dictionary.index.TestData

class TestQueryGeneralizer extends UnitSpec with ScratchDirectory {

  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)
  val searchers = Seq(searcher)

  it should "cover all PosTags" in {
    val tagSet = QueryLanguage.parser.posTagSet.toSet
    val generalizingTagset = QueryGeneralizer.posSets.reduce( (a,b) => a ++ b)
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
        QDisj(Seq(QPos("NN"), QPos("VBG"))), searchers, 10)
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
      val gens = QueryGeneralizer.queryGeneralizations(
        QRepetition(QWord("like"), 1, 4), searchers, 10)
      assert(gens == GeneralizeToDisj(Seq(QRepetition(QPos("VBP"), 1, 4))))
    }
    {
      val gens = QueryGeneralizer.queryGeneralizations(QRepetition(QPos("NN"), 1, 4), searchers, 10)
      val qexprs = gens.asInstanceOf[GeneralizeToDisj].elements

      assert(qexprs.size == 1)
      val repeatOf = qexprs.head.asInstanceOf[QRepeating].qexpr.asInstanceOf[QDisj].qexprs
      assert(repeatOf.contains(QPos("NNS")))
      assert(repeatOf.contains(QPos("NN")))
      assert(!repeatOf.contains(QPos("VB")))
    }
    assertResult(GeneralizeToNone())(QueryGeneralizer.queryGeneralizations(
      QRepetition(QWildcard(), 1, 4), searchers, 10))
  }
}
