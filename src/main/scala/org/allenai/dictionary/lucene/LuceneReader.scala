package org.allenai.dictionary.lucene

import scala.collection.JavaConverters._
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
import org.apache.lucene.search.spans.NearSpansUnordered
import org.apache.lucene.search.spans.Spans
import org.apache.lucene.search.spans.SpanQuery
import org.apache.lucene.search.spans.NearSpansOrdered2
import org.allenai.common.immutable.Interval

case object LuceneReader extends App {
  import Lucene._
  override def main(args: Array[String]): Unit = {
    val reader = LuceneReader(new File(args(0)))
    val nn = LTokenRegex(tokenDataFieldName, "POS=N.*", reader.reader)
    val any = LTokenRegex(tokenDataFieldName, "POS=.*", reader.reader)
    for {
      result <- reader.execute(LRepeat(nn, 0, 5))
      sent = result.sentence
      cap = result.matchOffset
      sub = sent.data.slice(cap.start, cap.end)
      s = sub.flatMap(_.attributes.filter(_.key == "CONTENT")).map(_.value).mkString(" ")
    } println(s)
  }
}

case class LuceneReader(path: File) {
  import Lucene._
  val directory = new NIOFSDirectory(path)
  val reader = DirectoryReader.open(directory)
  val searcher = new IndexSearcher(reader)
  val arc = reader.getContext.leaves().get(0)
  val bits = new Bits.MatchAllBits(reader.numDocs)
  val analyzer = new TokenDataAnalyzer
  
  def execute(expr: LuceneExpr, slop: Int = 0): Iterator[LuceneResult] = {
    val query = LuceneExpr.linearSpanNearQuery(expr, slop, true)
    println(query)
    val spans = getSpans(query)
    new ResultIterator(expr, spans)
  }
  
  def getSpans(spanQuery: SpanQuery): Spans = {
    val termContexts = new HashMap[Term, TermContext]
    spanQuery.getSpans(arc, bits, termContexts)
  }
  
  class ResultIterator(expr: LuceneExpr, spans: Spans) extends Iterator[LuceneResult] {
    var first = true
    var savedHasNext = false
    override def hasNext: Boolean = if (first) {
      spans.next
    } else {
      savedHasNext
    }
    override def next: LuceneResult = {
      first = false
      val offsets = spans match {
        case nso: NearSpansOrdered2 => captureOffsets(nso)
        case _ => Map.empty[String, Interval]
      }
      val result = LuceneResult(currentSentence, currentMatchOffset, offsets)
      savedHasNext = spans.next
      result
    }
    private def captureOffsets(multiSpans: NearSpansOrdered2): Map[String, Interval] = {
      val offsets = subOffsets(multiSpans)
      val pairs = for {
        (part, i) <- LuceneExpr.linearParts(expr).zipWithIndex
        result <- part match {
          case LCapture(_, name, _, _) => Some((name, offsets(i)))
          case _ => None
        }
      } yield result
      pairs.toMap
    }
    private def currentMatchOffset: Interval = Interval.open(spans.start, spans.end)
    private def currentSentence: IndexableSentence = {
      val doc = reader.document(spans.doc)
      IndexableSentence.fromLuceneDoc(doc)
    }
    private def subOffsets(multiSpans: NearSpansOrdered2): Array[Interval] = {
      val starts = multiSpans.getStarts
      val ends = multiSpans.getEnds
      for {
        (start, end) <- starts.zip(ends)
        i = Interval.open(start, end)
      } yield i
    }
  }
  
}
