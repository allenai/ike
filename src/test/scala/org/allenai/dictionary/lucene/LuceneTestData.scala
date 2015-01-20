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
import org.allenai.dictionary.QueryExprParser
import nl.inl.blacklab.index.Indexer
import org.allenai.dictionary.blacklab.AnnotationIndexer
import org.allenai.dictionary.blacklab.BlackLabFormat
import java.io.StringReader

object LuceneTestData {
  import Lucene._
  val resource = getClass.getResourceAsStream(s"/${CreateTestData.resourceName}")
  val lines = Source.fromInputStream(resource).getLines
  val namedAnnotatedText = lines.map(_.parseJson.convertTo[NamedAnnotatedText]).toSeq
  def writeTestIndex(path: File): Unit = {
    val indexer = new Indexer(path, true, classOf[AnnotationIndexer])
    for {
      named <- namedAnnotatedText
      name = named.name
      xml = BlackLabFormat.fromAnnotations(named)
    } indexer.index(name, new StringReader(xml.toString))
    indexer.close
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
    val doc1 = annotate("doc1", "I love the mango chunks. I love a mango chunks. I love a mango chunk.")
    val doc2 = annotate("doc2", "")
    val namedAnnotatedText = Seq(doc1, doc2)
    val docNames = namedAnnotatedText.map(_.name)
    val outputPath = new File(s"src/test/resources/${resourceName}")
    val out = new PrintWriter(outputPath)
    out.println(doc1.toJson)
    out.println(doc2.toJson)
    out.close
  }
}

object Foo extends App {
  import sext._
  val input = args.mkString(" ")
  val f = new File("foo")
  LuceneTestData.writeTestIndex(f)
  val reader = LuceneReader(f)
  val q = QueryExprParser.parse(input).get
  val l = reader.querySemantics(q)
  for {
    r <- reader.execute(l)
    sent = r.sentence
    words = sent.attributeSeqs(Seq("CONTENT", "POS")).map(_.mkString("/"))
    matchWords = words.slice(r.matchOffset.start, r.matchOffset.end).mkString(" ")
    groups = for {
      (key, value) <- r.captureGroups
      valueWords = words.slice(value.start, value.end).mkString(" ")
    } yield s"$key = $valueWords"
  } { println(words.mkString(" ")); println(matchWords); println(groups.mkString("\n")); println}

}