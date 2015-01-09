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
import org.apache.lucene.search.spans.NearSpansOrdered

case class LuceneReader(path: File) {
  import Lucene._
  val directory = new NIOFSDirectory(path)
  val reader = DirectoryReader.open(directory)
  val searcher = new IndexSearcher(reader)
  val arc = reader.getContext.leaves().get(0)
  val bits = new Bits.MatchAllBits(reader.numDocs)
  val analyzer = new TokenDataAnalyzer
}

case object LuceneReader extends App {
  import Lucene._
  override def main(args: Array[String]) = {
    val reader = LuceneReader(new File(args(0)))
    val x = "POS=IN"
    val y = "POS=JJ"
    val z = "POS=NN"
    val pq = new SpanTermQuery(new Term(tokenDataFieldName, x))
    val wq = new SpanTermQuery(new Term(tokenDataFieldName, y))
    val zq = new SpanTermQuery(new Term(tokenDataFieldName, z))
    val spanQuery = new SpanNearQuery(Array(pq, wq, zq), 0, true)
    val termContexts = new HashMap[Term, TermContext]
    val spans = spanQuery.getSpans(reader.arc, reader.bits, termContexts).asInstanceOf[NearSpansOrdered]
    while (spans.next) {
      val id = spans.doc
      val start = spans.start()
      val end = spans.end()
      val doc = reader.searcher.doc(id)
      val sent = IndexableSentence.fromLuceneDoc(doc)
      val matches = sent.data.slice(start, end)
      println(matches.mkString(" "))
      for {
        (s, k) <- spans.getSubSpans.zipWithIndex
        i = s.start
        j = s.end
        x = sent.data.slice(i, j).mkString(" ")
      } println (s"Clause $k [$i, $j) = $x")
      println
      /*val tokenStream = TokenSources.getAnyTokenStream(reader.reader, id, tokenDataFieldName, Lucene.analyzer)
      val wrapped = new TokenDataFilter(tokenStream)
      val charTermAttribute = wrapped.addAttribute(classOf[CharTermAttribute])
      val posAttr = wrapped.addAttribute(classOf[PositionIncrementAttribute])
      val tokens = new ListBuffer[TokenData]
      while (wrapped.incrementToken) {
        val terms = new ListBuffer[String]
        var term = charTermAttribute.toString
        var hasNext = true
        while (term != IndexableSentence.tokenSep && hasNext) {
          terms += term
          hasNext = wrapped.incrementToken
          term = charTermAttribute.toString
        }
        val tokenData = TokenData(terms map Attribute.fromString)
        tokens += tokenData
      }
      tokens.slice(start, end) foreach println
      println*/
    }
  }
  
}