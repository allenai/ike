package org.allenai.dictionary

import java.{ lang, util }

import org.allenai.common.Config._
import org.allenai.common.Logging

import com.medallia.word2vec.Searcher.{ Match, UnknownWordException }
import com.medallia.word2vec.{ Searcher, Word2VecModel }
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.collection.{ mutable, SeqView }
import scala.util.{ Try, Success, Failure }

trait SimilarPhrasesSearcher {
  def getSimilarPhrases(phrase: String): Seq[SimilarPhrase]
}

class WordVecPhraseSearcher(config: Config) extends Logging with SimilarPhrasesSearcher {

  val maxNumSimilarPhrases = 100

  private val model = {
    logger.info("Loading phrase vectors ...")
    val file = DataFile.fromDatastore(config[Config]("vectors"))
    val result = Word2VecModel.fromBinFile(file).forSearch()
    logger.info("Loading phrase vectors complete")
    result
  }

  /** Given a phrase, returns upto maxNumSimilarPhrases closest phrases.
    * @param phrase
    * @return
    */
  override def getSimilarPhrases(phrase: String): Seq[SimilarPhrase] = {
    val phraseWithUnderscores = phrase.replace(' ', '_').toLowerCase
    try {
      getSimilarPhrasesFromMatches(model.getMatches(phraseWithUnderscores, maxNumSimilarPhrases))
    } catch {
      case _: UnknownWordException => Seq.empty
    }
  }

  /** Given a vector, returns upto maxNumSimilarPhrases closest phrases.
    * @param vector
    * @return
    */
  def getSimilarPhrases(vector: Vector[Double]): Seq[SimilarPhrase] = {
    try {
      getSimilarPhrasesFromMatches(model.getMatches(vector.toArray, maxNumSimilarPhrases))
    } catch {
      case _: UnknownWordException => Seq.empty
    }
  }

  /** Helper Method to construct a Seq of SimilarPhrases from matches returned by the Searcher.
    * @param matches
    * @return
    */
  def getSimilarPhrasesFromMatches(matches: util.List[Match]): Seq[SimilarPhrase] = {
    matches.asScala.map { m =>
      val qwords = m.`match`().split("_").map(QWord)
      SimilarPhrase(qwords, m.distance())
    }
  }

  /** Returns a word2vec Vector for a given phrase if found.
    * @param phrase
    * @return
    */
  def getVectorForPhrase(phrase: String): Option[Vector[Double]] = {
    val phraseWithUnderscores = phrase.replace(' ', '_').toLowerCase
    try {
      Some(model.getRawVector(phraseWithUnderscores).asScala.toVector.map(_.doubleValue))
    } catch {
      case _: UnknownWordException => None
    }
  }

  /** Given a bunch of phrases, computes their centroid and determines n closest word2vec neighbors.
    * Utility function for table expansion.
    * @param phrases
    */
  def getCentroidMatches(phrases: Seq[String]): Seq[SimilarPhrase] = {
    val vectors = for {
      phrase <- phrases
      vector <- getVectorForPhrase(phrase)
    } yield vector

    if (vectors.length > 0) {
      val centroidVector = vectors.reduceLeft[Vector[Double]] { (v1, v2) => addVectors(v1, v2) } map
        (_ / vectors.length)
      getSimilarPhrases(centroidVector)
    } else Seq.empty[SimilarPhrase]
  }

  /** Helper Method to add two vectors of Doubles.
    */
  def addVectors(vector1: Vector[Double], vector2: Vector[Double]): Vector[Double] = {
    vector1 zip vector2 map { case (x, y) => x + y }
  }
}
