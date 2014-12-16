package org.allenai.dictionary

import scala.collection.JavaConverters._
import org.apache.lucene.util.Version
import org.apache.lucene.document.FieldType
import java.io.File
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.store.NIOFSDirectory
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.util.Bits
import org.apache.lucene.search.spans.SpanQuery
import scala.collection.mutable.ListBuffer
import java.util.HashMap
import org.apache.lucene.index.Term
import org.apache.lucene.index.TermContext
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.search.highlight.TokenSources
import org.apache.lucene.sandbox.queries.regex.RegexQuery
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper
import org.apache.lucene.index.FieldInfo
import org.apache.lucene.search.spans.SpanTermQuery
import org.apache.lucene.search.spans.SpanNearQuery

case class LuceneReader(path: File) {
  import Lucene._
  val directory = new NIOFSDirectory(path)
  val reader = DirectoryReader.open(directory)
  val searcher = new IndexSearcher(reader)
  val arc = reader.getContext.leaves().get(0)
  val bits = new Bits.MatchAllBits(reader.numDocs)
  
  def clusterPrefixQuery(prefix: String): SpanQuery = {
    val queryString = s"${clusterTokenPrefix}${prefix}.*"
    val query = new RegexQuery(new Term(clusterFieldName, queryString))
    val rewritten = new SpanMultiTermQueryWrapper(query).rewrite(reader)
    rewritten.asInstanceOf[SpanQuery]
  }
  
  def wordQuery(word: String): SpanQuery = new SpanTermQuery(new Term(clusterFieldName, word))
  
  def sequenceQuery(qs: Seq[SpanQuery]): SpanQuery = new SpanNearQuery(qs.toArray, 0, true)
  
  def matches(spanQuery: SpanQuery): Iterable[String] = {
    val results = new ListBuffer[String]
    val termContexts = new HashMap[Term, TermContext]
    val spans = spanQuery.getSpans(arc, bits, termContexts)
    while (spans.next) {
      val id = spans.doc
      val start = spans.start()
      val end = spans.end()
      val doc = searcher.doc(id)
      val tokenStream = TokenSources.getAnyTokenStream(reader, id, fieldName, plainAnalyzer)
      val charTermAttribute = tokenStream.addAttribute(charTermAttrClass)
      var terms = new ListBuffer[String]
      var pos = 0
      while (tokenStream.incrementToken) {
        if (start <= pos && pos < end) {
          val term = charTermAttribute.toString
          terms += term
        }
        pos += 1
      }
      val matched = terms.mkString(" ")
      results += matched 
    }
    results.toSeq
  }
}

case class LuceneWriter(path: File, wordClusters: Map[String, String]) {
  import Lucene._
  val clusterAnalyzer = new ClusterAnalyzer(wordClusters)
  val analyzerMap = Map(fieldName -> plainAnalyzer, clusterFieldName -> clusterAnalyzer)
  val analyzer = new PerFieldAnalyzerWrapper(plainAnalyzer, analyzerMap.asJava)
  val directory = new NIOFSDirectory(path)
  val config = new IndexWriterConfig(version, analyzer)
  def writer = new IndexWriter(directory, config)
  def makeDoc(text: String): Document = {
    val doc = new Document
    doc.add(new Field(fieldName, text, fieldType))
    doc.add(new Field(clusterFieldName, text, fieldType))
    doc
  }
  def write(texts: Iterator[String]): Unit = {
    val w = writer
    val docs = texts map makeDoc foreach w.addDocument 
    w.commit
  }
}

object Lucene {
  val version = Version.LUCENE_48
  val fieldName = "text"
  val clusterFieldName = s"${fieldName}Clusters"
  val fieldType = {
    val f = new FieldType
    f.setIndexed(true)
    f.setStored(true)
    f.setStoreTermVectors(true)
    f.setStoreTermVectorOffsets(true)
    f.freeze
    f
  }
  val clusterTokenPrefix = "CLUSTER"
  val charTermAttrClass = classOf[CharTermAttribute]
  val plainAnalyzer = new PlainAnalyzer
}