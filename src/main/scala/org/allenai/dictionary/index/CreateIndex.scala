package org.allenai.dictionary.index

import java.net.URI
import java.nio.file.{ Files, Paths }

import nl.inl.blacklab.index.Indexer
import java.io.StringReader
import java.io.File
import org.allenai.datastore.Datastore
import org.allenai.nlpstack.core.Lemmatized
import org.allenai.nlpstack.core.PostaggedToken

object CreateIndex extends App {
  def addTo(indexer: Indexer)(text: IndexableText): Unit = {
    val xml = XmlSerialization.xml(text)
    val id = text.idText.id
    indexer.index(id, new StringReader(xml.toString))
  }

  case class Options(
    destinationDir: File = null,
    batchSize: Int = 1000,
    clusters: URI = null,
    textSource: URI = null
  )

  val parser = new scopt.OptionParser[Options](this.getClass.getSimpleName.stripSuffix("$")) {
    opt[File]('d', "destination") required () action { (d, o) =>
      o.copy(destinationDir = d)
    } text "Directory to create the index in"

    opt[Int]('b', "batchSize") action { (b, o) =>
      o.copy(batchSize = b)
    } text "Batch size"

    opt[URI]('c', "clusters") required () action { (c, o) =>
      o.copy(clusters = c)
    } text "URL of a file to load clusters from"

    opt[URI]('t', "textSource") required () action { (t, o) =>
      o.copy(textSource = t)
    } text "URL of a file or directory to load the text from"

    help("help")
  }

  parser.parse(args, Options()) foreach { options =>
    val indexDir = options.destinationDir
    val batchSize = options.batchSize
    val clusterFile = options.clusters.getScheme match {
      case "file" => Paths.get(options.clusters).toFile
      case "datastore" => Datastore.locatorFromUrl(options.clusters).path.toFile
      case otherAuthority =>
        throw new RuntimeException(s"URL scheme not supported: $otherAuthority")
    }
    val clusters = Clusters.fromFile(clusterFile)
    var numAdded = 0
    val idTexts = options.textSource.getScheme match {
      case "file" => {
        val path = Paths.get(options.textSource)
        if (Files.isDirectory(path))
          IdText.fromDirectory(path.toFile)
        else
          IdText.fromFlatFile(path.toFile)
      }
      case "datastore" => {
        val locator = Datastore.locatorFromUrl(options.textSource)
        if (locator.directory)
          IdText.fromDirectory(locator.path.toFile)
        else
          IdText.fromFlatFile(locator.path.toFile)
      }
      case otherAuthority =>
        throw new RuntimeException(s"URL scheme not supported: $otherAuthority")
    }

    def indexableToken(lemmatized: Lemmatized[PostaggedToken]): IndexableToken = {
      val word = lemmatized.token.string
      val wordLc = word.toLowerCase
      val pos = lemmatized.token.postag
      val lemma = lemmatized.lemma
      val cluster = clusters.getOrElse(wordLc, "")
      IndexableToken(word, pos, lemma, cluster)
    }

    def process(idText: IdText): IndexableText = {
      val text = idText.text
      val sents = for {
        sent <- NlpAnnotate.annotate(text)
        indexableSent = sent map indexableToken
      } yield indexableSent
      IndexableText(idText, sents)
    }

    def processBatch(batch: Seq[IdText]): Seq[IndexableText] = batch.toArray.par.map(process).seq

    def addTo(indexer: Indexer)(text: IndexableText): Unit = {
      CreateIndex.addTo(indexer)(text)
      numAdded = numAdded + 1
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
