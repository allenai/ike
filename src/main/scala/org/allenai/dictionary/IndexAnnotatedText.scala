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
import java.io.File

class IndexAnnotatedText(config: Config) {

  val fileName = config.getString("input.fileName")
  val input = config.getConfig("input")
  val inputDir = input.getString("location") match {
    case "file" => new File(input.getString("path"))
    case "datastore" =>
      val ref = DatastoreRef.fromConfig(input.getConfig("item"))
      ref.directoryPath.toFile
    case _ =>
      throw new IllegalArgumentException(s"'location' must be either 'file' or 'datastore'")
  }
  val inputFile = new File(inputDir, fileName)
  val outputDir = new File(config.getString("output.path"))
  val sentencePerDoc = config.getBoolean("sentencePerDoc")
  val chunkSize = config.getInt("chunkSize")

  def lines: Iterator[String] = Source.fromFile(inputFile).getLines

  def annotatedText: Iterator[NamedAnnotatedText] =
    lines map (_.parseJson.convertTo[NamedAnnotatedText])

  def makeBlackLabDocs(named: NamedAnnotatedText): Seq[BlackLabDocument] =
    if (sentencePerDoc) toBlackLabSentenceDocs(named) else Seq(toBlackLabDocument(named))

  val indexer = new Indexer(outputDir, true, classOf[AnnotationIndexer])

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
    }
  }

  def close(): Unit = indexer.close

  def index(): Unit = {
    val groups = annotatedText.grouped(chunkSize)
    groups foreach {
      group => group.par foreach add
    }
    close
  }
}

object IndexAnnotatedText extends App {
  override def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load
    val indexer = new IndexAnnotatedText(config.getConfig("buildIndex"))
    indexer.index
  }
}
