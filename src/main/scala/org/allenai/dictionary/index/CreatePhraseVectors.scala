package org.allenai.dictionary.index

import java.io.{ FileOutputStream, File }
import java.util.concurrent.atomic.AtomicLong

import com.medallia.word2vec.Word2VecModel
import com.medallia.word2vec.Word2VecTrainerBuilder.TrainingProgressListener
import com.medallia.word2vec.neuralnetwork.NeuralNetworkType
import com.medallia.word2vec.util.Format
import org.allenai.common.{ Resource, Logging }
import org.allenai.common.ParIterator._
import org.allenai.nlpstack.segment.{ StanfordSegmenter => segmenter }
import org.allenai.nlpstack.tokenize.{ defaultTokenizer => tokenizer }

import java.net.URI
import java.nio.file.Files

import org.apache.thrift.TSerializer

import scala.collection.immutable.TreeSet
import scala.collection.mutable
import scala.collection.concurrent
import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.JavaConverters._
import Ordering.Implicits._

object CreatePhraseVectors extends App with Logging {
  // phrase2vec parameters
  val minWordCount = 5
  val startThreshold = 200

  case class Options(input: URI = null, destination: File = null)

  val parser = new scopt.OptionParser[Options](this.getClass.getSimpleName.stripSuffix("$")) {
    opt[URI]('i', "input") required () action { (i, o) =>
      o.copy(input = i)
    } text "URL of the input file"

    opt[File]('d', "destination") required () action { (d, o) =>
      o.copy(destination = d)
    } text "the destination vector file"

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
        val result = try {
          segmenter.segment(idText.text).map { sentence =>
            tokenizer.tokenize(sentence.text).map { token =>
              token.string.toLowerCase
            }
          }
        } catch {
          case _: StackOverflowError =>
            logger.warn("Stack overflow when tokenizing. Ignoring document.")
            logger.info("Untokenizable text:")
            logger.info(idText.text)
            Seq()
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
      * In doing this it is greedy, not clever. If "for the" and "the common good" are known
      * phrases, it will return [for the] [common] [good], because it doesn't figure out that it
      * could get a longer phrase in a different way. We might be able to do better, but this is how
      * the original phrase2vec code did it.
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
      val bigUnigramCounts = new concurrent.TrieMap[Phrase, Int]
      val bigBigramCounts = new concurrent.TrieMap[(Phrase, Phrase), Int]
      def bumpBigCount[T](map: concurrent.Map[T, Int], key: T, increase: Int): Unit = {
        val prev = map.putIfAbsent(key, 1)
        prev match {
          case Some(count) =>
            val success = map.replace(key, count, count + increase)
            if (!success) bumpBigCount(map, key, increase)
          case None => // yay!
        }
      }

      phrasifiedSentences(phrases).grouped(1024).parForeach {
        sentences =>
          {
            val unigramCounts = mutable.Map[Phrase, Int]()
            val bigramCounts = mutable.Map[(Phrase, Phrase), Int]()
            def bumpCount[T](map: mutable.Map[T, Int], key: T): Unit = {
              val count = map.getOrElse(key, 0) + 1
              map.update(key, count)
            }

            sentences.foreach { sentence =>
              sentence.foreach(bumpCount(unigramCounts, _))
              if (sentence.length >= 2) {
                sentence.sliding(2).foreach {
                  case Seq(left, right) =>
                    bumpCount(bigramCounts, (left, right))
                }
              }
            }

            unigramCounts.foreach {
              case (unigram, count) =>
                bumpBigCount(bigUnigramCounts, unigram, count)
            }
            bigramCounts.foreach {
              case (bigram, count) =>
                bumpBigCount(bigBigramCounts, bigram, count)
            }
          }
      }

      def applyMinWordCount[T](map: mutable.Map[T, Int]) =
        map.filter { case (_, count) => count >= minWordCount }
      (applyMinWordCount(bigUnigramCounts), applyMinWordCount(bigBigramCounts))
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
    val phrases = (0 until 3).foldLeft(new OrderedPrefixSet) {
      case (p, i) =>
        logger.info(s"Starting round ${i + 1} of making phrases.")
        val result = updatePhrases(p, startThreshold / (1 << i))
        logger.info(s"Found ${result.size} phrases")
        result
    }

    logger.info("Building vectors based on the phrases")

    val tokensIterable = new java.lang.Iterable[java.util.List[String]] {
      override def iterator = new java.util.Iterator[java.util.List[String]] {
        private val inner = phrasifiedSentences(phrases)

        override def next(): java.util.List[String] =
          inner.next().map(_.mkString("_")).toList.asJava
        override def hasNext: Boolean = inner.hasNext
      }
    }

    // train the model
    val model: Word2VecModel = Word2VecModel.trainer.
      setMinVocabFrequency(minWordCount).
      setWindowSize(8).
      `type`(NeuralNetworkType.CBOW).
      setLayerSize(200).
      useNegativeSamples(25).
      setDownSamplingRate(1e-4).
      setNumIterations(5).
      setListener(new TrainingProgressListener {
        private var lastMessagePrinted = System.currentTimeMillis()

        override def update(stage: TrainingProgressListener.Stage, progress: Double): Unit = {
          val now = System.currentTimeMillis()
          if (progress >= 100 || now - lastMessagePrinted > 5000) {
            lastMessagePrinted = now
            logger.info(
              "Stage '%s' is %1.2f%% complete".format(Format.formatEnum(stage), progress * 100)
            )
          }
        }
      }).train(tokensIterable)

    // store the model
    val modelByteArray = new TSerializer().serialize(model.toThrift)
    Resource.using(new FileOutputStream(options.destination))(_.write(modelByteArray))
    logger.info(s"Wrote ${modelByteArray.length} bytes to ${options.destination}")
  }
}
