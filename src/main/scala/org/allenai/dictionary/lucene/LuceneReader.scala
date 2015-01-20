package org.allenai.dictionary.lucene

import scala.collection.JavaConverters._
import java.io.File
import org.apache.lucene.store.NIOFSDirectory
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.util.Bits
import org.apache.lucene.index.Term
import java.util.HashMap
import org.apache.lucene.index.TermContext
import org.apache.lucene.search.highlight.TokenSources
import scala.collection.mutable.ListBuffer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute
import org.allenai.common.immutable.Interval
import org.allenai.dictionary.QueryExpr
import org.allenai.dictionary.Content
import org.allenai.dictionary.PosTag
import org.allenai.dictionary.ClusterPrefix
import org.allenai.dictionary.QueryCapture
import org.allenai.dictionary.QueryNonCapture
import org.allenai.dictionary.WildCard
import org.allenai.dictionary.QuerySeq
import org.allenai.dictionary.QueryDisjunction
import org.allenai.dictionary.QueryExprParser
import org.allenai.dictionary.NamedQueryCapture
import org.allenai.dictionary.QueryStar
import org.allenai.dictionary.QueryPlus
import org.apache.lucene.search.spans.SpanQuery
import org.apache.lucene.search.spans.NearSpansOrdered
import nl.inl.blacklab.search.lucene.BLSpans
import nl.inl.blacklab.search.Span
import org.apache.lucene.search.spans.Spans
import nl.inl.blacklab.search.Searcher
import nl.inl.blacklab.search.Hits

case object LuceneReader extends App {
  import Lucene._
  
  override def main(args: Array[String]): Unit = {
    val reader = LuceneReader(new File(args(0)))
    val nn = LTokenRegex(tokenDataFieldName, "POS=N.*", reader.reader)
    val any = LTokenRegex(tokenDataFieldName, "CONTENT=.*ion", reader.reader)
    for {
      result <- reader.execute(LSeq(nn, any))
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
  val bsearcher = Searcher.open(path)
  
  import IndexableSentence._
  
  def querySemantics(qexpr: QueryExpr): LuceneExpr = qexpr match {
    case Content(value) => LTokenMatch(tokenDataFieldName, Attribute(contentAttr, value).toString)
    case PosTag(value) => LTokenMatch(tokenDataFieldName, Attribute(posTagAttr, value).toString)
    case ClusterPrefix(value) => LTokenRegex(tokenDataFieldName, Attribute(clusterTagAttr, value).toString, reader)
    case QueryCapture(expr) => LCapture(querySemantics(expr), expr.pos.toString)
    case NamedQueryCapture(expr, name) => LCapture(querySemantics(expr), name)
    case QueryNonCapture(expr) => querySemantics(expr)
    case WildCard => LTokenRegex(tokenDataFieldName, Attribute(posTagAttr, ".*").toString, reader)
    case QuerySeq(children) => LSeq(children map querySemantics)
    case QueryDisjunction(children) => LDisjunction(children map querySemantics)
    case QueryPlus(expr) => LRepeat(querySemantics(expr), 1, 2)
    case _ => throw new IllegalArgumentException(s"Unimplemented!")
  }
  
  import sext._
  def execute(expr: LuceneExpr, slop: Int = 0): Iterator[LuceneResult] = {
    val query = LuceneExpr.linearSpanNearQuery(expr, slop, true)
    val hits = new Hits(bsearcher, query)
    val result = for {
      hit <- hits.asScala
      offset = Interval.open(hit.start, hit.end)
      groups = hits.getCapturedGroups(hit)
      offsets = groups.map(spans => Interval.open(spans.start, spans.end))
      names = hits.getCapturedGroupNames      
      sent = IndexableSentence.fromLuceneDoc(reader.document(hit.doc))
    } yield LuceneResult(sent, offset, names.asScala.zip(offsets).toMap)
    result.iterator
  }
  
  def getSpans(spanQuery: SpanQuery): Spans = {
    val termContexts = new HashMap[Term, TermContext]
    spanQuery.getSpans(arc, bits, termContexts)
  }
  
  class ResultIterator(expr: LuceneExpr, spans: Spans) extends Iterator[LuceneResult] {
    var parts = LuceneExpr.linearParts(expr)
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
        case bls: BLSpans => captureOffsets(bls)
        case _ => Map.empty[String, Interval]
      }
      val result = LuceneResult(currentSentence, currentMatchOffset, offsets)
      savedHasNext = spans.next
      result
    }
    private def captureOffsets(multiSpans: BLSpans): Map[String, Interval] = {
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
    private def subOffsets(multiSpans: BLSpans): Array[Interval] = {
      println(multiSpans)
      val array: Array[Span] = Array.fill(parts.size)(null)
      array foreach println
      multiSpans.getCapturedGroups(array)
      array foreach println
      array map { span => Interval.open(span.start, span.end) }
    }
  }
  
}
