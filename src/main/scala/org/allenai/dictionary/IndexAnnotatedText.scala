package org.allenai.dictionary

import com.typesafe.config.ConfigFactory
import java.io.File
import scala.io.Source
import spray.json._
import DefaultJsonProtocol._
import org.allenai.scholar.text.NamedAnnotatedText
import nl.inl.blacklab.index.Indexer
import AnnotatedTextConversion._
import XmlSerialization._
import java.io.StringReader
import com.typesafe.config.Config

class IndexAnnotatedText(config: Config) {
  val indexPath = new File(config.getString("indexPath"))
  val sentencePerDoc = config.getBoolean("sentencePerDoc")
  var numIndexed = 0
  
  def makeBlackLabDocs(named: NamedAnnotatedText): Seq[BlackLabDocument] =
    if (sentencePerDoc) toBlackLabSentenceDocs(named) else Seq(toBlackLabDocument(named))
  
  val indexer = new Indexer(indexPath, true, classOf[AnnotationIndexer])
  def add(s: String): Unit = try {
    val text = s.parseJson.convertTo[NamedAnnotatedText]
    add(text)
  } catch {
    case e: Exception => System.err.println(s"Could not parse line: ${e.getMessage}")
  }
  def add(named: NamedAnnotatedText): Unit = {
    for (doc <- makeBlackLabDocs(named)) {
      val name = named.name
      val xml = toXml(doc)
      indexer.index(name, new StringReader(xml.toString))
      numIndexed += 1
    }
  }
  def close: Unit = indexer.close
}

object IndexAnnotatedText extends App {
  override def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load
    val indexer = new IndexAnnotatedText(config)
    val inputPath = new File(config.getString("annotatedText"))
    val lines = Source.fromFile(inputPath).getLines
    val chunkSize = 100
    val groups = lines.grouped(chunkSize)
    groups foreach { group =>
      group.par foreach indexer.add
    }
    indexer.close
  }
}