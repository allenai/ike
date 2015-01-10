package org.allenai.dictionary.lucene

import org.apache.lucene.search.spans.SpanQuery
import org.apache.lucene.search.spans.SpanTermQuery
import org.apache.lucene.index.Term
import org.apache.lucene.search.spans.SpanOrQuery
import org.apache.lucene.search.spans.SpanNearQuery2
import org.apache.lucene.search.spans.SpanNearQuery
import org.apache.lucene.sandbox.queries.regex.RegexQuery
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper
import org.apache.lucene.index.IndexReader

sealed trait LuceneExpr {
  def spanQuery: SpanQuery
}

object LuceneExpr {
  def linearParts(expr: LuceneExpr): Seq[LuceneExpr] = expr match {
    case t: LTokenMatch => Seq(t)
    case r: LTokenRegex => Seq(r)
    case seq: LSeq => seq.parts.flatMap(linearParts)
    case cap: LCapture => Seq(cap)
    case dis: LDisjunction => Seq(dis)
  }
  def linearSpanNearQuery(expr: LuceneExpr, slop: Int = 0, inOrder: Boolean = true): SpanNearQuery2 = {
    val subQueries = linearParts(expr).map(_.spanQuery).toArray
    new SpanNearQuery2(subQueries, slop, inOrder)
  }
}

case class LTokenRegex(fieldName: String, pattern: String, reader: IndexReader) extends LuceneExpr {
  override def spanQuery: SpanQuery = {
    val q = new RegexQuery(new Term(fieldName, pattern))
    val rw = new SpanMultiTermQueryWrapper(q)
    rw.rewrite(reader).asInstanceOf[SpanQuery]
  } 
}

case class LTokenMatch(fieldName: String, fieldValue: String) extends LuceneExpr {
  override def spanQuery: SpanQuery = new SpanTermQuery(new Term(fieldName, fieldValue))
}

case class LSeq(parts: Seq[LuceneExpr], slop: Int = 0, inOrder: Boolean = true) extends LuceneExpr {
  override def spanQuery: SpanQuery = {
    val subQueries = parts.map(_.spanQuery).toArray
    new SpanNearQuery2(subQueries, slop, inOrder)
  }
}

case class LCapture(expr: LuceneExpr, name: String, slop: Int = 0, inOrder: Boolean = true) extends LuceneExpr {
  override def spanQuery: SpanQuery = {
    val subQueries = Array(expr.spanQuery)
    new SpanNearQuery2(subQueries, slop, inOrder)
  }
}

case class LDisjunction(left: LuceneExpr, right: LuceneExpr) extends LuceneExpr {
  override def spanQuery: SpanQuery = {
    val lquery = left.spanQuery
    val rquery = right.spanQuery
    new SpanOrQuery(lquery, rquery)
  }
}