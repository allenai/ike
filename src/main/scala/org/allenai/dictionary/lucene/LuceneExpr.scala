package org.allenai.dictionary.lucene

import org.apache.lucene.index.Term
import org.apache.lucene.index.IndexReader
import org.apache.lucene.search.RegexpQuery
import org.apache.lucene.search.spans.SpanQuery
import org.apache.lucene.search.spans.SpanTermQuery
import nl.inl.blacklab.search.sequences.SpanQuerySequence
import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper
import nl.inl.blacklab.search.lucene.BLSpanOrQuery
import nl.inl.blacklab.search.lucene.SpanQueryCaptureGroup


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
    case rep: LRepeat => Seq(rep)
  }
  def linearSpanNearQuery(expr: LuceneExpr, slop: Int = 0, inOrder: Boolean = true): SpanQuerySequence = {
    val subQueries = linearParts(expr).map(_.spanQuery).toArray
    new SpanQuerySequence(subQueries)
  }
}

case class LTokenRegex(fieldName: String, pattern: String, reader: IndexReader) extends LuceneExpr {
  override def spanQuery: SpanQuery = {
    val q = new RegexpQuery(new Term(fieldName, pattern))
    val rw = new BLSpanMultiTermQueryWrapper(q)
    rw.rewrite(reader).asInstanceOf[SpanQuery]
  } 
}

case class LTokenMatch(fieldName: String, fieldValue: String) extends LuceneExpr {
  override def spanQuery: SpanQuery = new SpanTermQuery(new Term(fieldName, fieldValue))
}

case class LSeq(parts: Seq[LuceneExpr], slop: Int = 0, inOrder: Boolean = true) extends LuceneExpr {
  override def spanQuery: SpanQuery = {
    val subQueries = parts.map(_.spanQuery).toArray
    new SpanQuerySequence(subQueries)
  }
}

case object LSeq {
  def apply(parts: LuceneExpr*): LSeq = LSeq(parts)
}

case class LCapture(expr: LuceneExpr, name: String, slop: Int = 0, inOrder: Boolean = true) extends LuceneExpr {
  override def spanQuery: SpanQuery = {
    val subQueries = new SpanQuerySequence(Array(expr.spanQuery))
    new SpanQueryCaptureGroup(subQueries, name)
  }
}

case class LDisjunction(exprs: Seq[LuceneExpr]) extends LuceneExpr {
  override def spanQuery: SpanQuery = {
    val queries = exprs.map(_.spanQuery)
    new BLSpanOrQuery(queries:_*)
  }
}

case class LRepeat(expr: LuceneExpr, min: Int, max: Int) extends LuceneExpr {
  override def spanQuery: SpanQuery = {
    val parts = for {
      i <- min to max
      seq = List.fill(i)(expr)
    } yield LSeq(seq).spanQuery
    new BLSpanOrQuery(parts.reverse:_*)
  }
}