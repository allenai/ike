package org.allenai.dictionary

import com.medallia.word2vec.Searcher.UnknownWordException
import com.medallia.word2vec.Word2VecModel
import com.typesafe.config.Config
import org.allenai.common.Config._
import org.allenai.common.Logging
import scala.collection.JavaConverters._

trait SimilarPhrasesSearcher {
  def getSimilarPhrases(phrase: String): Seq[SimilarPhrase]
}

class WordVecPhraseSearcher(config: Config) extends Logging with SimilarPhrasesSearcher {
  private val model = {
    logger.info("Loading phrase vectors ...")
    val file = DataFile.fromDatastore(config[Config]("vectors"))
    val result = Word2VecModel.fromBinFile(file).forSearch()
    logger.info("Loading phrase vectors complete")
    result
  }

  override def getSimilarPhrases(phrase: String): Seq[SimilarPhrase] = {
    val phraseWithUnderscores = phrase.replace(' ', '_')
    try {
      model.getMatches(phraseWithUnderscores, 100).asScala.map { m =>
        val qwords = m.`match`().split("_").map(QWord)
        SimilarPhrase(qwords, m.distance())
      }
    } catch {
      case _: UnknownWordException => Seq.empty
    }
  }
}
