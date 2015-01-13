package org.allenai.dictionary.lucene

import scala.io.Source
import spray.json._
import DefaultJsonProtocol._
import org.allenai.scholar.text.NamedAnnotatedText
import java.io.File
import Lucene._
import IndexableSentence.{contentAttr, posTagAttr}
import org.apache.lucene.index.IndexReader
import org.allenai.scholar.text.AnnotatedText
import org.allenai.scholar.pipeline.annotate.AnnotateNlp
import java.io.PrintWriter

object LuceneTestData {
  import Lucene._
  val resource = getClass.getResourceAsStream(s"/${CreateTestData.resourceName}")
  val lines = Source.fromInputStream(resource).getLines
  val namedAnnotatedText = lines.map(_.parseJson.convertTo[NamedAnnotatedText]).toSeq
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

object CreateTestData extends App {
  def resourceName = "named-annotated-text.json"
  def annotate(name: String, content: String): NamedAnnotatedText = {
    val text = NamedAnnotatedText(name, new AnnotatedText(content, Seq.empty))
    AnnotateNlp.annotate(text)
  }
  override def main(args: Array[String]): Unit = {
    val doc1 = annotate("doc1", "I eat frozen fruit daily. My favorite is frozen mango chunks.")
    val doc2 = annotate("doc2", "Cooking popcorn is easy. I prefer coconut oil.")
    val namedAnnotatedText = Seq(doc1, doc2)
    val docNames = namedAnnotatedText.map(_.name)
    val outputPath = new File(s"src/test/resources/${resourceName}")
    val out = new PrintWriter(outputPath)
    out.println(doc1.toJson)
    out.println(doc2.toJson)
    out.close
  }
}