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
import scala.math.pow

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
    def idTexts = {
      val path = CliUtils.pathFromUri(options.input)
      if (Files.isDirectory(path)) {
        IdText.fromDirectory(path.toFile)
      } else {
        IdText.fromFlatFile(path.toFile)
      }
    }

    type Phrase = Seq[String]

    def sentences: Iterator[Phrase] = {
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

    type OrderedPrefixSet = TreeSet[Phrase]

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

    def updatePhrases(phrases: OrderedPrefixSet, threshold: Int): OrderedPrefixSet = {
      val (unigramCounts, bigramCounts) = phraseCounts(phrases)
      val wordCount = unigramCounts.values.sum

      val newPhrases = bigramCounts.flatMap {
        case ((left, right), bigramCount) =>
          for {
            leftCount <- unigramCounts.get(left)
            rightCount <- unigramCounts.get(right)
            score = (wordCount * (bigramCount - minWordCount)) / (leftCount * rightCount)
            if score > threshold
          } yield {
            left ++ right
          }
      }

      phrases ++ newPhrases
    }

    var phrases = new OrderedPrefixSet
    for (i <- 0 until 3) {
      logger.info(s"Starting round ${i + 1} of making phrases.")
      phrases = updatePhrases(phrases, startThreshold / (1 << i))
      logger.info(s"Found ${phrases.size} phrases")
    }
    phrases.foreach(println)
  }
}
