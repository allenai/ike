package org.allenai.dictionary.index

import java.io._
import java.util
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.{ GZIPInputStream, GZIPOutputStream }

import com.medallia.word2vec.Word2VecModel
import com.medallia.word2vec.Word2VecTrainerBuilder.TrainingProgressListener
import com.medallia.word2vec.neuralnetwork.NeuralNetworkType
import com.medallia.word2vec.util.Format
import org.allenai.common.{ StreamClosingIterator, Resource, Logging }
import org.allenai.common.ParIterator._
import org.allenai.nlpstack.segment.{ StanfordSegmenter => segmenter }
import org.allenai.nlpstack.tokenize.{ defaultTokenizer => tokenizer }

import java.net.URI
import java.nio.file.Files

import org.apache.thrift.TSerializer

import scala.collection.immutable.TreeSet
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.JavaConverters._
import Ordering.Implicits._
import scala.io.Source
import scala.util.Sorting

object CreatePhraseVectors extends App with Logging {
  // phrase2vec parameters
  val minWordCount = 5
  val startThreshold = 200

  case class Options(
    input: URI = null,
    destination: File = null,
    vocabSize: Int = 100000000,
    sentencesFile: Option[File] = None,
    phrasesFile: Option[File] = None,
    phrasifiedCorpusFile: Option[File] = None
  )

  val parser = new scopt.OptionParser[Options](this.getClass.getSimpleName.stripSuffix("$")) {
    opt[URI]('i', "input") required () action { (i, o) =>
      o.copy(input = i)
    } text "URL of the input file"

    opt[File]('d', "destination") required () action { (d, o) =>
      o.copy(destination = d)
    } text "the destination vector file"

    opt[Int]('v', "vocabSize") action { (v, o) =>
      o.copy(vocabSize = v)
    } text "the maximum size of the vocabulary"

    opt[File]('s', "sentences") action { (s, o) =>
      o.copy(sentencesFile = Some(s))
    } text "File to read pre-tokenized sentences for, one sentence per line. If the file does " +
      "not exist, it is created."

    opt[File]('p', "phrases") action { (p, o) =>
      o.copy(phrasesFile = Some(p))
    } text "File to read phrase definitions from if it exists, or write them to if it doesn't."

    opt[File]("phrasifiedCorpus") action { (p, o) =>
      o.copy(phrasifiedCorpusFile = Some(p))
    } text "File to read the phrasified corpus from. If it does not exist, it is created."

    help("help")
  }

  parser.parse(args, Options()) foreach { options =>

    /** Returns an iterator of documents in IdText format
      */
    def idTexts: Iterator[IdText] = new Iterator[IdText] {
      private val documentCount = new AtomicLong()
      private var oldDocumentCount: Long = 0
      private val byteCount = new AtomicLong()
      private var oldByteCount: Long = 0
      private val lastMessagePrinted = new AtomicLong(System.currentTimeMillis())

      private val inner = {
        val path = CliUtils.pathFromUri(options.input)
        if (Files.isDirectory(path)) {
          IdText.fromDirectory(path.toFile)
        } else if (path.toString.endsWith(".gz")) {
          IdText.fromWikipedia(path.toFile)
        } else {
          IdText.fromFlatFile(path.toFile)
        }
      }

      override def hasNext: Boolean = inner.hasNext
      override def next(): IdText = {
        val result = inner.next

        val currentDocumentCount = documentCount.incrementAndGet()
        val currentByteCount = byteCount.addAndGet(result.text.length)
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
      }
    }

    /** Turns the documents into an iterator of sentences
      */
    def makeSentences(docs: Iterator[IdText]): Iterator[Seq[String]] = {
      docs.parMap { idText =>
        try {
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
      }.flatten
    }

    /** Writes tokenized sentences to a file, so we can read them from there quickly afterwards
      * @param sentences the tokenized sentences
      * @param sentencesCacheFile the file to write to
      */
    def writeSentencesCacheFile(sentences: Iterator[Seq[String]], sentencesCacheFile: File): Unit = {
      Resource.using(new FileOutputStream(sentencesCacheFile)) { os =>
        val gzipStream = new GZIPOutputStream(os)
        val writer = new BufferedWriter(new OutputStreamWriter(gzipStream, "UTF-8"))

        sentences.foreach { sentence =>
          sentence.foreach { token =>
            writer.write(token)
            writer.write(' ')
          }
          writer.write('\n')
        }

        writer.flush()
        gzipStream.finish()
        os.flush()
      }
    }

    def readSentencesCacheFile(sentencesCacheFile: File): Iterator[Seq[String]] = {
      StreamClosingIterator(new GZIPInputStream(new FileInputStream(sentencesCacheFile))) { is =>
        val lines = new ProgressLoggingIterator(Source.fromInputStream(is, "UTF-8").getLines())
        lines.map(_.split(" "))
      }
    }

    lazy val sentencesCacheFile = options.sentencesFile match {
      case None =>
        val file = File.createTempFile("CreatePhraseVectors.SentencesCache-", ".txt.gz")
        file.deleteOnExit()
        logger.info(s"Making a cache of tokenized sentences at $file")
        writeSentencesCacheFile(makeSentences(idTexts), file)
        file
      case Some(file) =>
        if (!file.exists()) {
          logger.info(s"Making a cache of tokenized sentences at $file")
          writeSentencesCacheFile(makeSentences(idTexts), file)
        } else {
          logger.info(s"Reading tokenized sentences from $file")
        }
        file
    }

    def sentencesFromCache = readSentencesCacheFile(sentencesCacheFile)

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
    def phrasifySentence(phrases: OrderedPrefixSet, sentence: Seq[String]) = {
      // phrasifies sentences greedily
      def phrasesStartingWith(start: Seq[String]) =
        phrases.from(start).takeWhile(_.startsWith(start))

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
      def reduceMapToVocabSize[T](map: mutable.Map[T, Int]): Unit = map.synchronized {
        // Have to check twice. All threads will get here, only one reduces the vocab while
        // the others wait.
        if (map.size > options.vocabSize) {
          // This would be a prime example for the QuickSelect algorithm, which can do this in
          // O(n), but in practice the difference between O(n) and O(n log n) isn't worth the
          // trouble.
          val counts = map.values.toArray
          Sorting.quickSort(counts)
          val minCount = counts(counts.length - options.vocabSize - 1)

          logger.info(s"Reducing vocab; removing all items with counts <= $minCount")
          logger.info(s"Size before: ${map.size}")

          map.foreach { case (k, c) => if (c <= minCount) map.remove(k) }

          logger.info(s"Size after:  ${map.size}")

          System.gc()
        }
      }

      val bigUnigramCounts = mutable.Map[Phrase, Int]()
      val bigBigramCounts = mutable.Map[(Phrase, Phrase), Int]()
      def bumpBigCount[T](map: mutable.Map[T, Int], key: T, increase: Int): Unit = {
        val prev = map.getOrElse(key, 0)
        map.put(key, prev + increase)
        if (map.size > 2 * options.vocabSize) reduceMapToVocabSize(map)
      }

      sentencesFromCache.grouped(1024).parForeach({ unphrasifiedSentences =>
        val phrasifiedSentences = unphrasifiedSentences.map(phrasifySentence(phrases, _))

        val unigramCounts = mutable.Map[Phrase, Int]()
        val bigramCounts = mutable.Map[(Phrase, Phrase), Int]()
        def bumpCount[T](map: mutable.Map[T, Int], key: T): Unit = {
          val count = map.getOrElse(key, 0) + 1
          map.update(key, count)

          // if the map is now bigger than the vocab size, cut down to vocab size
          if (map.size > 2 * options.vocabSize) reduceMapToVocabSize(map)
        }

        phrasifiedSentences.foreach { sentence =>
          sentence.foreach(bumpCount(unigramCounts, _))
          if (sentence.length >= 2) {
            sentence.sliding(2).foreach {
              case Seq(left, right) =>
                bumpCount(bigramCounts, (left, right))
            }
          }
        }

        bigUnigramCounts.synchronized {
          unigramCounts.foreach {
            case (unigram, count) =>
              bumpBigCount(bigUnigramCounts, unigram, count)
          }
        }

        bigBigramCounts.synchronized {
          bigramCounts.foreach {
            case (bigram, count) =>
              bumpBigCount(bigBigramCounts, bigram, count)
          }
        }
      }, 8)

      reduceMapToVocabSize(bigUnigramCounts)
      reduceMapToVocabSize(bigUnigramCounts)
      (bigUnigramCounts, bigBigramCounts)
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
            if leftCount > 0
            rightCount <- unigramCounts.get(right)
            if rightCount > 0
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
    def makePhrases = (0 until 3).foldLeft(new OrderedPrefixSet) {
      case (p, i) =>
        logger.info(s"Starting round ${i + 1} of making phrases.")
        val result = updatePhrases(p, startThreshold / (1 << i))
        logger.info(s"Found ${result.size} phrases")
        result
    }

    val phrases = options.phrasesFile match {
      case None => makePhrases
      case Some(phrasesFile) if !phrasesFile.exists() =>
        logger.info(s"Writing phrases to $phrasesFile")
        val result = makePhrases
        Resource.using(new PrintWriter(phrasesFile, "UTF-8")) { writer =>
          result.foreach { phrase =>
            writer.println(phrase.mkString("_"))
          }
        }
        result
      case Some(phrasesFile) =>
        logger.info(s"Reading phrases from $phrasesFile")
        val lines = StreamClosingIterator(new FileInputStream(phrasesFile)) { is =>
          Source.fromInputStream(is, "UTF-8").getLines()
        }
        lines.map(_.split("_")).foldLeft(new OrderedPrefixSet) {
          case (set, phrase) =>
            set + phrase
        }
    }

    def writePhrasifiedCorpusCache(
      phrasifiedSentences: Iterator[Seq[Phrase]],
      phrasifiedCorpusCacheFile: File
    ): Unit = Resource.using(new FileOutputStream(phrasifiedCorpusCacheFile)) { os =>
      logger.info(s"Making a cache of the phrasified corpus at $phrasifiedCorpusCacheFile")
      val gzipStream = new GZIPOutputStream(os)
      val writer = new BufferedWriter(new OutputStreamWriter(gzipStream, "UTF-8"))

      phrasifiedSentences.foreach { sentence =>
        sentence.foreach { phrase =>
          writer.write(phrase.mkString("_"))
          writer.write(' ')
        }
        writer.write('\n')
      }

      writer.flush()
      gzipStream.finish()
      os.flush()
    }

    def readPhrasifiedCorpusCache(phrasifiedCorpusCacheFile: File): Iterator[Seq[String]] = {
      StreamClosingIterator(new GZIPInputStream(new FileInputStream(phrasifiedCorpusCacheFile))) { is =>
        val lines = new ProgressLoggingIterator(Source.fromInputStream(is, "UTF-8").getLines())
        lines.map(_.split(" "))
      }
    }

    val phrasifiedCorpus: Iterable[Seq[String]] = {
      def makePhrasifiedCorpus = sentencesFromCache.parMap(phrasifySentence(phrases, _))
      val cacheFile = options.phrasifiedCorpusFile match {
        case None =>
          val file = File.createTempFile("CreatePhraseVectors.PhrasifiedCorpusCache-", ".txt.gz")
          file.deleteOnExit()
          writePhrasifiedCorpusCache(makePhrasifiedCorpus, file)
          file
        case Some(file) =>
          if (!file.exists()) {
            writePhrasifiedCorpusCache(makePhrasifiedCorpus, file)
          } else {
            logger.info(s"Reading phrasified corpus from $file")
          }
          file
      }

      new Iterable[Seq[String]] {
        override def iterator: Iterator[Seq[String]] = readPhrasifiedCorpusCache(cacheFile)
      }
    }

    logger.info("Building vectors based on the phrases")

    val tokensIterable = new java.lang.Iterable[java.util.List[String]] {
      override def iterator = new java.util.Iterator[java.util.List[String]] {
        private val inner = phrasifiedCorpus.iterator
        override def next(): java.util.List[String] =
          new util.ArrayList[String](inner.next().toList.asJava)
        override def hasNext: Boolean = inner.hasNext
      }
    }

    // train the model
    val trainer = Word2VecModel.trainer.
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
      })

    val model: Word2VecModel = trainer.train(tokensIterable)

    // store the model
    val modelByteArray = new TSerializer().serialize(model.toThrift)
    Resource.using(new FileOutputStream(options.destination))(_.write(modelByteArray))
    logger.info(s"Wrote ${modelByteArray.length} bytes to ${options.destination}")
  }
}

class ProgressLoggingIterator(inner: Iterator[String]) extends Iterator[String] with Logging {
  private val byteCount = new AtomicLong()
  private var oldByteCount: Long = 0
  private val lastMessagePrinted = new AtomicLong(System.currentTimeMillis())

  override def hasNext: Boolean = {
    val result = inner.hasNext
    if (!result)
      logger.info("Done reading strings")
    result
  }
  override def next(): String = {
    val result = inner.next()

    val currentByteCount = byteCount.addAndGet(result.length)
    val last = lastMessagePrinted.get()
    val now = System.currentTimeMillis()
    val elapsed = (now - last).toDouble / 1000
    if (elapsed > 5 && lastMessagePrinted.compareAndSet(last, now)) {
      val bytesProcessed = currentByteCount - oldByteCount
      oldByteCount = currentByteCount
      val kbps = bytesProcessed.toDouble / elapsed / 1000

      logger.info("Read strings at %1.2f kb/s".format(kbps))
    }

    result
  }
}
