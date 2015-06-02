package org.allenai.dictionary.index

import org.allenai.datastore.Datastore
import org.allenai.nlpstack.core.{ ChunkedToken, Lemmatized }

import nl.inl.blacklab.index.Indexer

import java.io.{ File, StringReader }
import java.net.URI
import java.nio.file.{ Files, Paths }

object CreateIndex extends App {
  def addTo(indexer: Indexer)(text: IndexableText): Unit = {
    val xml = XmlSerialization.xml(text)
    val id = text.idText.id
    indexer.index(id, new StringReader(xml.toString()))
  }

  case class Options(
    destinationDir: File = null,
    batchSize: Int = 1000,
    textSource: URI = null,
    oneSentencePerDoc: Boolean = true
  )

  val parser = new scopt.OptionParser[Options](this.getClass.getSimpleName.stripSuffix("$")) {
    opt[File]('d', "destination") required () action { (d, o) =>
      o.copy(destinationDir = d)
    } text "Directory to create the index in"

    opt[Int]('b', "batchSize") action { (b, o) =>
      o.copy(batchSize = b)
    } text "Batch size"

    opt[URI]('t', "textSource") required () action { (t, o) =>
      o.copy(textSource = t)
    } text "URL of a file or directory to load the text from"

    opt[Unit]("oneSentencePerDoc") action { (_, o) =>
      o.copy(oneSentencePerDoc = true)
    }

    help("help")
  }

  parser.parse(args, Options()) foreach { options =>
    val indexDir = options.destinationDir
    val batchSize = options.batchSize
    val idTexts = options.textSource.getScheme match {
      case "file" =>
        val path = Paths.get(options.textSource)
        if (Files.isDirectory(path)) {
          IdText.fromDirectory(path.toFile)
        } else {
          IdText.fromFlatFile(path.toFile)
        }
      case "datastore" =>
        val locator = Datastore.locatorFromUrl(options.textSource)
        if (locator.directory) {
          IdText.fromDirectory(locator.path.toFile)
        } else {
          IdText.fromFlatFile(locator.path.toFile)
        }
      case otherAuthority =>
        throw new RuntimeException(s"URL scheme not supported: $otherAuthority")
    }

    def indexableToken(lemmatized: Lemmatized[ChunkedToken]): IndexableToken = {
      val word = lemmatized.token.string
      val pos = lemmatized.token.postag
      val lemma = lemmatized.lemma
      val chunk = lemmatized.token.chunk
      IndexableToken(word, pos, lemma, chunk)
    }

    def process(idText: IdText): Seq[IndexableText] = {
      if (options.oneSentencePerDoc) {
        val sents = NlpAnnotate.annotate(idText.text)
        sents.zipWithIndex.filter(_._1.nonEmpty).map {
          case (sent, index) =>
            val text = idText.text.substring(
              sent.head.token.offset,
              sent.last.token.offset + sent.last.token.string.length
            )
            val sentenceIdText = IdText(s"${idText.id}-$index", text)

            IndexableText(sentenceIdText, Seq(sent map indexableToken))
        }
      } else {
        val text = idText.text
        val sents = for {
          sent <- NlpAnnotate.annotate(text)
          indexableSent = sent map indexableToken
        } yield indexableSent
        Seq(IndexableText(idText, sents))
      }
    }

    def processBatch(batch: Seq[IdText]): Seq[IndexableText] =
      batch.toArray.par.map(process).flatten.seq

    def addTo(indexer: Indexer)(text: IndexableText): Unit = {
      CreateIndex.addTo(indexer)(text)
    }

    val indexer = new Indexer(indexDir, true, classOf[AnnotationIndexer])
    val indexableTexts = for {
      batch <- idTexts.grouped(batchSize)
      batchResults = processBatch(batch)
      result <- batchResults
    } yield result
    indexableTexts foreach addTo(indexer)
    indexer.close()
  }
}
