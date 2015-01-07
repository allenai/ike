package org.allenai.dictionary.lucene

import scala.io.Source
import spray.json._
import DefaultJsonProtocol._
import org.allenai.scholar.text.NamedAnnotatedText
import java.io.File

object CreateIndex extends App {
  val documentPath = args(0)
  val indexPath = args(1)
  val lines = Source.fromFile(documentPath).getLines
  val texts = lines.map(_.parseJson.convertTo[NamedAnnotatedText].text)
  val strings = texts.flatMap(Text.fromAnnotatedText)
  val writer = LuceneWriter(new File(indexPath))
  writer.write(strings)
}