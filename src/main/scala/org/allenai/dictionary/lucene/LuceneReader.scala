package org.allenai.dictionary.lucene

import java.io.File
import org.apache.lucene.store.NIOFSDirectory
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.util.Bits
import org.apache.lucene.search.spans.SpanTermQuery
import org.apache.lucene.index.Term
import org.apache.lucene.search.spans.SpanNearQuery
import java.util.HashMap
import org.apache.lucene.index.TermContext
import org.apache.lucene.search.highlight.TokenSources
import scala.collection.mutable.ListBuffer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute

case class LuceneReader(path: File) {
  import Lucene._
  val directory = new NIOFSDirectory(path)
  val reader = DirectoryReader.open(directory)
  val searcher = new IndexSearcher(reader)
  val arc = reader.getContext.leaves().get(0)
  val bits = new Bits.MatchAllBits(reader.numDocs)
  val analyzer = new TokenAttributeAnalyzer
}

case object LuceneReader extends App {
  import Lucene._
  override def main(args: Array[String]) = {
    val reader = LuceneReader(new File(args(0)))
    val x = "POS=DT"
    val y = "POS=NN"
    val z = "POS=NN"
    val pq = new SpanTermQuery(new Term(fieldName, x))
    val wq = new SpanTermQuery(new Term(fieldName, y))
    val zq = new SpanTermQuery(new Term(fieldName, z))
    val spanQuery = new SpanNearQuery(Array(pq, wq, zq), 0, true)
    val termContexts = new HashMap[Term, TermContext]
    val spans = spanQuery.getSpans(reader.arc, reader.bits, termContexts)
    while (spans.next) {
      val id = spans.doc
      val start = spans.start()
      val end = spans.end()
      println(id + " " + start + " " + end)
      val doc = reader.searcher.doc(id)
      val tokenStream = TokenSources.getAnyTokenStream(reader.reader, id, fieldName, reader.analyzer)
      val charTermAttribute = tokenStream.addAttribute(classOf[CharTermAttribute])
      var terms = new ListBuffer[String]
      var pos = -1
      while (tokenStream.incrementToken) {
        val term = charTermAttribute.toString
        if (DocToken.isContent(term)) {
          pos += 1
        }
        if (start <= pos && pos < end) {
          terms += term
        }
        
      }
      println(terms.mkString(" "))
    }
  }
  
}