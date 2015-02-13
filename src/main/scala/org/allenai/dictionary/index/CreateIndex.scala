package org.allenai.dictionary.index

import nl.inl.blacklab.index.Indexer
import java.io.StringReader
import java.io.File
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import org.allenai.dictionary.DataFile
import org.allenai.nlpstack.core.Lemmatized
import org.allenai.nlpstack.core.PostaggedToken
import org.allenai.common.Logging
import scala.concurrent.duration.Duration
import org.allenai.common.Timing

case class CreateIndex(config: Config) extends Logging {
  
  val indexDir = new File(config.getString("indexDir"))
  val defaultBatchSize = 1000
  val batchSize = if (config.hasPath("batchSize")) config.getInt("batchSize") else defaultBatchSize
  val clusterFile = DataFile.fromConfig(config.getConfig("clusters"))
  val clusters = Clusters.fromFile(clusterFile)
  var numAdded = 0
  
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
  
  def create(): Unit = {
    val start = System.currentTimeMillis
    val indexer = new Indexer(indexDir, true, classOf[AnnotationIndexer])
    val idTexts = IdText.fromConfig(config.getConfig("text"))
    val indexableTexts = for {
      batch <- idTexts.grouped(batchSize)
      batchResults = processBatch(batch)
      result <- batchResults
    } yield result
    indexableTexts foreach addTo(indexer)
    val end = System.currentTimeMillis
    indexer.close
  }
  
  def addTo(indexer: Indexer)(text: IndexableText): Unit = {
    val xml = XmlSerialization.xml(text)
    val id = text.idText.id
    indexer.index(id, new StringReader(xml.toString))
    numAdded = numAdded + 1
  }
  
}

object CreateIndex {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load.getConfig("CreateIndex")
    CreateIndex(config).create
  }
}
