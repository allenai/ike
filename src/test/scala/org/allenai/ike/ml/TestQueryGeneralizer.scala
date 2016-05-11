package org.allenai.ike.ml

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.ike._
import org.allenai.ike.index.TestData

class TestQueryGeneralizer extends UnitSpec with ScratchDirectory {

  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)
  val searchers = Seq(searcher)
  val ss = new SimilarPhrasesSearcherStub(Map(
    "i" -> Seq(
      SimilarPhrase(Seq(QWord("It")), 0.8),
      SimilarPhrase(Seq(QWord("like")), 0.4)
    )
  ))
  val qsimForI = QSimilarPhrases(Seq(QWord("i")), 2, ss.getSimilarPhrases("i"))

  it should "cover all PosTags" in {
    // This test will raise an error if there are
    val tagSet = QueryLanguage.parser.posTagSet.toSet - "FW"
    val generalizingTagset = QueryGeneralizer.posSets.reduce(_ ++ _)
    assert(tagSet == generalizingTagset)
  }

  it should "suggest correct generalizations" in {
    {
      val gens = QueryGeneralizer.queryGeneralizations(QPos("NN"), searchers, ss, 10)
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
      val gens = QueryGeneralizer.queryGeneralizations(QWord("i"), searchers, ss, 10)
      assertResult(gens)(GeneralizeToDisj(Seq(QPos("PRP")), Seq(qsimForI), true))
    }
    {
      val testQuery = QDisj(Seq(
        QPos("NN"), QSimilarPhrases(Seq(QWord("i")), 1, ss.getSimilarPhrases("i"))
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
