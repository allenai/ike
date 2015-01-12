package org.allenai.dictionary.lucene

import scala.io.Source
import spray.json._
import DefaultJsonProtocol._
import org.allenai.scholar.text.NamedAnnotatedText
import java.io.File
import Lucene._
import IndexableSentence.{contentAttr, posTagAttr}
import org.apache.lucene.index.IndexReader

object LuceneTestData {
  import Lucene._
  lazy val namedAnnotatedText = {
    val resourceName = "/named-annotated-text.json"
    val resourceStream = getClass.getResourceAsStream(resourceName)
    val lines = Source.fromInputStream(resourceStream).getLines
    lines.map(_.parseJson.convertTo[NamedAnnotatedText]).toSeq
  }
  lazy val docNames = namedAnnotatedText.map(_.name)
  def writeTestIndex(path: File): LuceneWriter = {
    val isents = namedAnnotatedText.flatMap(IndexableSentence.fromText)
    val writer = LuceneWriter(path)
    writer.write(isents.iterator)
    writer
  }
  def contentEq(s: String): LuceneExpr =
    LTokenMatch(tokenDataFieldName, Attribute(contentAttr, s).toString)
  def posEq(s: String): LuceneExpr =
    LTokenMatch(tokenDataFieldName, Attribute(posTagAttr, s).toString)
  def posRegex(s: String, reader: IndexReader): LuceneExpr = 
    LTokenRegex(tokenDataFieldName, Attribute(posTagAttr, s).toString, reader)
  def contentRegex(s: String, reader: IndexReader): LuceneExpr = 
    LTokenRegex(tokenDataFieldName, Attribute(contentAttr, s).toString, reader)
}