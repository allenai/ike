package org.allenai.dictionary.index

import java.util.concurrent.atomic.{ AtomicLong, AtomicInteger }

import org.allenai.common.Logging
import org.allenai.common.ParIterator._
import org.allenai.nlpstack.segment.{ defaultSegmenter => segmenter }
import org.allenai.nlpstack.tokenize.{ defaultTokenizer => tokenizer }

import java.net.URI
import java.nio.file.Files

import scala.collection.concurrent
import scala.collection.immutable.TreeSet
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import Ordering.Implicits._

object CreatePhraseVectors extends App with Logging {
  // phrase2vec parameters
  val minWordCount = 5
  val startThreshold = 200

  case class Options(input: URI = null, destination: URI = null)

  val parser = new scopt.OptionParser[Options](this.getClass.getSimpleName.stripSuffix("$")) {
    opt[URI]('i', "input") required () action { (i, o) =>
      o.copy(input = i)
    } text "URL of the input file"

    opt[URI]('d', "destination") required () action { (d, o) =>
      o.copy(destination = d)
    } text "URL of the destination vector file"

    help("help")
  }

  parser.parse(args, Options()) foreach { options =>

    /** Returns an iterator of documents in IdText format
      */
    def idTexts: Iterator[IdText] = {
      val path = CliUtils.pathFromUri(options.input)
      if (Files.isDirectory(path)) {
        IdText.fromDirectory(path.toFile)
      } else {
        IdText.fromFlatFile(path.toFile)
      }
    }

    /** Turns the documents into an iterator of sentences
      */
    def sentences: Iterator[Seq[String]] = {
      val documentCount = new AtomicLong()
      var oldDocumentCount: Long = 0
      val byteCount = new AtomicLong()
      var oldByteCount: Long = 0
      val lastMessagePrinted = new AtomicLong(System.currentTimeMillis())

      idTexts.parMap { idText =>
        val result = segmenter.segment(idText.text).map { sentence =>
          tokenizer.tokenize(sentence.text).map { token =>
            token.string.toLowerCase
          }
        }

        val currentDocumentCount = documentCount.incrementAndGet()
        val currentByteCount = byteCount.addAndGet(idText.text.length)
        val last = lastMessagePrinted.get()
        val now = System.currentTimeMillis()
        val elapsed = (now - last).toDouble / 1000
        if (elapsed > 5 && lastMessagePrinted.compareAndSet(last, now)) {
          val documentsProcessed = currentDocumentCount - oldDocumentCount
          oldDocumentCount = currentDocumentCount
          val dps = documentsProcessed.toDouble / elapsed

          val bytesProcessed = currentByteCount - oldByteCount
          oldByteCount = currentByteCount
          val kbps = bytesProcessed.toDouble / elapsed / 1000

          logger.info("Read %d documents, %1.2f docs/s, %1.2f kb/s".
            format(currentDocumentCount, dps, kbps))
        }

        result
      }.flatten
    }

    type Phrase = Seq[String]
    type OrderedPrefixSet = TreeSet[Phrase]

    /** Turns the sentences into list of phrases. A phrase is the combination (i.e., a Seq), of one
      * or more tokens.
      *
      * For example, if the input phrase is "for the common good", and "common good" is one of the
      * known phrases in the phrases parameter, the output will be this: [for] [the] [common good].
      * In doing this it is greedy, not clever. If "for the" and "the common good" are known phrases,
      * it will return [for the] [common] [good], because it doesn't figure out that it could get a
      * longer phrase in a different way. We might be able to do better, but this is how the original
      * phrase2vec code did it.
      *
      * @param phrases the phrases we know about
      */
    def phrasifiedSentences(phrases: OrderedPrefixSet) = sentences.map { sentence =>
      // phrasifies sentences greedily
      def phrasesStartingWith(start: Phrase) = phrases.from(start).takeWhile(_.startsWith(start))

      val result = mutable.Buffer[Phrase]()
      var prefix = Seq.empty[String]
      for (token <- sentence) {
        val newPrefix = prefix :+ token
        if (phrasesStartingWith(newPrefix).isEmpty) {
          if (prefix.nonEmpty) result += prefix
          prefix = Seq(token)
        } else {
          prefix = newPrefix
        }
      }
      result += prefix
      result.toSeq
    }

    /** Counts phrases in the input
      * @param phrases the phrases we know about
      * @return two maps, one with unigram counts, and one with bigram counts
      */
    def phraseCounts(phrases: OrderedPrefixSet) = {
      val unigramCounts = new concurrent.TrieMap[Phrase, Int]
      val bigramCounts = new concurrent.TrieMap[(Phrase, Phrase), Int]
      def bumpCount[T](map: concurrent.Map[T, Int], key: T): Unit = {
        val prev = map.putIfAbsent(key, 1)
        prev match {
          case Some(count) =>
            val success = map.replace(key, count, count + 1)
            if (!success) bumpCount(map, key)
          case None => // yay!
        }
      }

      phrasifiedSentences(phrases).foreach { sentence =>
        sentence.foreach(bumpCount(unigramCounts, _))
        if (sentence.length >= 2) {
          sentence.sliding(2).foreach {
            case Seq(left, right) =>
              bumpCount(bigramCounts, (left, right))
          }
        }
      }

      def applyMinWordCount[T](map: concurrent.Map[T, Int]) =
        map.filter { case (_, count) => count >= minWordCount }
      (applyMinWordCount(unigramCounts), applyMinWordCount(bigramCounts))
    }

    /** Given a set of phrases and a threshold, returns a new set of longer phrases.
      *
      * @param phrases   a set of phrases we already know about
      * @param threshold the score threshold for new phrases
      * @return a new set of phrases
      */
    def updatePhrases(phrases: OrderedPrefixSet, threshold: Int): OrderedPrefixSet = {
      val (unigramCounts, bigramCounts) = phraseCounts(phrases)
      val wordCount = unigramCounts.values.sum

      val newPhrasesMap = bigramCounts.flatMap {
        case ((left, right), bigramCount) =>
          for {
            leftCount <- unigramCounts.get(left)
            rightCount <- unigramCounts.get(right)
            score = (wordCount * (bigramCount - minWordCount)) / (leftCount * rightCount)
            if score > threshold
          } yield {
            (left ++ right, score)
          }
      }

      val topN = 25
      logger.info(s"Top $topN phrases by score:")
      newPhrasesMap.toSeq.sortBy(-_._2).take(topN).foreach {
        case (phrase, score) =>
          val stringPhrase = phrase.mkString(" ")
          logger.info(s"$stringPhrase ($score)")
      }

      phrases ++ newPhrasesMap.keys
    }

    // update phrases three times
    // Maybe it would be better if we ran this until we don't find any new phrases?
    var phrases = new OrderedPrefixSet
    for (i <- 0 until 3) {
      logger.info(s"Starting round ${i + 1} of making phrases.")
      phrases = updatePhrases(phrases, startThreshold / (1 << i))
      logger.info(s"Found ${phrases.size} phrases")
    }
    phrases.foreach(println)
  }
}
