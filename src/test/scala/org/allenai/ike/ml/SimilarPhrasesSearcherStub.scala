package org.allenai.ike.ml

import org.allenai.ike.{ SimilarPhrase, SimilarPhrasesSearcher }

class SimilarPhrasesSearcherStub(phrases: Map[String, Seq[SimilarPhrase]] = Map())
    extends SimilarPhrasesSearcher {
  override def getSimilarPhrases(phrase: String): Seq[SimilarPhrase] = {
    phrases.getOrElse(phrase, Seq())
  }
  override def getSimilarPhrases(phraseSeq: Seq[String]): Seq[SimilarPhrase] = {
    Seq.empty
  }
}
