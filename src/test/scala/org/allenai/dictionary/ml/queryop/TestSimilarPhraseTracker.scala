package org.allenai.dictionary.ml.queryop

import org.allenai.common.testkit.UnitSpec
import org.allenai.dictionary.{ QSimilarPhrases, QWord, SimilarPhrase }

class TestSimilarPhraseTracker extends UnitSpec {

  def tos(str: String): IndexedSeq[String] = {
    str.split(" ").toIndexedSeq
  }
  "minSimForPhrases" should "test correctly" in {
    val simStrs = List(
      ("c d a", 0.97),
      ("b", 0.93),
      ("a b", 0.9),
      ("c", 0.85),
      ("c d", 0.8),
      ("a c", 0.7),
      ("d", 0.6),
      ("b c", 0.25),
      ("a e", 0.2),
      ("a", 0.1)
    )
    val strRanks = ("e" :: simStrs.map(_._1)).zipWithIndex.toMap
    val simPhrases = simStrs.map {
      case (str, sim) =>
        SimilarPhrase(str.split(" ").map(QWord), sim)
    }.toSeq
    val qSimilarPhrases = QSimilarPhrases(Seq(QWord("e")), simPhrases.size, simPhrases)
    val tracker = new SimilarPhraseMatchTracker(qSimilarPhrases)

    // Sanity check
    assertResult(strRanks("a"))(tracker.minSimForPhrases(tos("a"), 0, 5))
    assertResult(0)(tracker.minSimForPhrases(tos("e"), 0, 5))
    assertResult(0)(tracker.minSimForPhrases(IndexedSeq(), 0, 5))

    // Find that <a b> <c> is better than <a> <b> <c>
    assertResult(strRanks("c"))(tracker.minSimForPhrases(tos("a b c"), 0, 5))

    // Find <c d a> <e> is best
    assertResult(strRanks("c d a"))(tracker.minSimForPhrases(tos("c d a e"), 0, -1))

    // Min should stop us using <c d a>
    assertResult(strRanks("a e"))(tracker.minSimForPhrases(tos("c d a e"), 3, -1))

    // Find <b> <c> <c> is best
    assertResult(strRanks("c"))(tracker.minSimForPhrases(tos("b c c"), 0, 3))

    // Max should force us to use <b c> <c>
    assertResult(strRanks("b c"))(tracker.minSimForPhrases(tos("b c c"), 0, 2))
  }
}
