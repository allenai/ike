package org.allenai.dictionary.ml

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.dictionary._
import org.allenai.dictionary.index.TestData

class TestQueryGeneralizer extends UnitSpec with ScratchDirectory {

  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)
  val searchers = Seq(searcher)
  val ss = new SimilarPhrasesSearcherStub(Map(
    "I" -> Seq(
      SimilarPhrase(Seq(QWord("It")), 0.8),
      SimilarPhrase(Seq(QWord("like")), 0.4))
  ))
  val qsimForI = QSimilarPhrases(Seq(QWord("I")), 2, ss.getSimilarPhrases("I"))

  it should "cover all PosTags" in {
    val tagSet = QueryLanguage.parser.posTagSet.toSet
    val generalizingTagset = QueryGeneralizer.posSets.reduce((a, b) => a ++ b)
    assert((tagSet == generalizingTagset))
  }

  it should "suggest correct generalizations" in {
    {
      val gens = QueryGeneralizer.queryGeneralizations(QPos("NN"), (searchers), ss, 10)
      val qexprs = gens.asInstanceOf[GeneralizeToDisj].pos
      assert(qexprs.contains(QPos("NNS")))
      assert(!qexprs.contains(QPos("VBG")))
      assert(!qexprs.contains(QPos("NN")))
    }
    {
      val gens = QueryGeneralizer.queryGeneralizations(
        QDisj(Seq(QPos("NN"), QPos("VBG"))), searchers, ss, 10
      )
      val qexprs = gens.asInstanceOf[GeneralizeToDisj].pos
      assert(qexprs.contains(QPos("NNS")))
      assert(qexprs.contains(QPos("VB")))
      assert(!qexprs.contains(QPos("JJ")))
      assert(!qexprs.contains(QPos("JJS")))
      assert(!qexprs.contains(QPos("VBG")))
    }
    {
      val gens = QueryGeneralizer.queryGeneralizations(QWord("I"), searchers, ss, 10)
      assert(gens == GeneralizeToDisj(Seq(QPos("PRP")), Seq(qsimForI), true))
    }
    {
      val testQuery = QDisj(Seq(
        QPos("NN"), QSimilarPhrases(Seq(QWord("I")), 1, ss.getSimilarPhrases("I"))
      ))
      val gens = QueryGeneralizer.queryGeneralizations(testQuery, searchers, ss, 10).
          asInstanceOf[GeneralizeToDisj]
      assert(gens.pos.map(_.value).toSet == (QueryGeneralizer.posSets(1) - "NN" + "PRP"))
      assert(gens.phrase == Seq(qsimForI))
      assert(!gens.fullyGeneralizes)
    }

    assertResult(GeneralizeToNone())(QueryGeneralizer.queryGeneralizations(
      QRepetition(QWildcard(), 1, 4), searchers, ss, 10
    ))
  }
}
