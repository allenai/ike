package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.lucene.{ SpanQueryAnd, SpanQueryCaptureGroup }
import nl.inl.blacklab.search.sequences.SpanQuerySequence
import nl.inl.blacklab.search.{ Hits, Searcher }
import org.allenai.dictionary._
import org.allenai.dictionary.ml._
import org.apache.lucene.search.spans.SpanQuery

import scala.collection.JavaConverters._

object GeneralizedQuerySampler {

  /** Builds a SpanQuery that matches the generalized version of `tokenizedQuery`. If `limitTo`
    * is defined it contains (table, startDoc, startToken) and the returned query will only match
    * rows from table and will only return Hits after startDoc and startToken
    */
  def buildGeneralizedSpanQuery(
    tokenizedQuery: TokenizedQuery,
    searcher: Searcher,
    tables: Map[String, Table],
    posSampleSize: Int,
    limitTo: Option[(Table, Int, Int)]
  ): SpanQuery = {
    def buildSpanQuery(qexpr: QExpr): SpanQuery = {
      searcher.createSpanQuery(
        BlackLabSemantics.blackLabQuery(QueryLanguage.interpolateTables(qexpr, tables).get)
      )
    }
    def phrase2QExpr(phrase: Seq[QWord]): QExpr = {
      if (phrase.size == 1) phrase.head else QSeq(phrase)
    }
    val generalizations = tokenizedQuery.generalizations.get

    val oneCapture = tokenizedQuery.tokenSequences.count(_.isInstanceOf[CapturedTokenSequence]) == 1

    // Build span queries for each query/generalization
    val generalizingSpanQueries = generalizations.zip(tokenizedQuery.getNamedTokens).map {
      case (GeneralizeToDisj(qpos, qsimiliar, _), (name, original)) =>
        val originalSq = buildSpanQuery(original)
        // 'Flatten' the qSimQueries into individual QExpr, this gives us the chance to filter
        // out repeats and minimize nesting of Disjunction queries
        val qSimQueries = qsimiliar.flatMap { qsim =>
          phrase2QExpr(qsim.qwords) +: qsim.phrases.map(sp => phrase2QExpr(sp.qwords))
        }
        val extensions = (qpos ++ qSimQueries).distinct.map(buildSpanQuery)
        new SpanQueryTrackingDisjunction(originalSq, extensions, name)
      // Currently we do not handle GeneralizeToAll
      case (_, (name, original)) => buildSpanQuery(QNamed(original, name))
    }

    // Group the span queries into chunks and wrap the right chunks in capture groups
    var remaining = generalizingSpanQueries
    var chunked = List[SpanQuery]()
    tokenizedQuery.tokenSequences.foreach { ts =>
      val (chunk, rest) = remaining.splitAt(ts.size)
      remaining = rest
      val next = ts match {
        case CapturedTokenSequence(_, name, _) =>
          if (oneCapture && limitTo.isDefined) {
            // Use SpanQueryAnd to make sure this capture group must match tokens with in the
            // given table
            val filteredRows = Sampler.getFilteredRows(tokenizedQuery, limitTo.get._1)
            val tableQExpr = QDisj(filteredRows.map(x => QSeq(x.head)))
            val tableSpanQuery = buildSpanQuery(tableQExpr)
            Seq(new SpanQueryCaptureGroup(new SpanQueryAnd(
              new SpanQuerySequence(chunk.asJava), tableSpanQuery
            ), name))
          } else {
            Seq(new SpanQueryCaptureGroup(new SpanQuerySequence(chunk.asJava), name))
          }
        case TokenSequence(_) => chunk
      }
      chunked = chunked ++ next
    }
    require(remaining.isEmpty)
    val spanQuery = new SpanQuerySequence(chunked.asJava)
    limitTo match {
      case Some((_, doc, token)) =>
        if (oneCapture) {
          // We already limited to the query by ANDing it when building chunked
          new SpanQueryStartAt(spanQuery, doc, token)
        } else {
          val captureGroups = tokenizedQuery.tokenSequences.flatMap {
            case cts: CapturedTokenSequence => Some(cts.captureName)
            case _ => None
          }
          val tableQexpr = Sampler.buildLabelledQuery(tokenizedQuery, limitTo.get._1)
          new SpanQueryFilterByCaptureGroups(
            spanQuery,
            buildSpanQuery(tableQexpr), captureGroups, doc, token
          )
        }
      case _ => spanQuery
    }
  }
}

/** Sampler that returns hits that could be matched by the generalizations of the input
  * query.
  *
  * @param maxEdits maximum edits a sentence can be from the query to be returned
  * @param posSampleSize number of Hits to sample when deciding what POS to generalize words to
  */
case class GeneralizedQuerySampler(maxEdits: Int, posSampleSize: Int)
    extends Sampler() {

  require(maxEdits >= 0)
  require(posSampleSize > 0)

  def buildGeneralizingQuery(tokenizedQuery: TokenizedQuery, searcher: Searcher,
    tables: Map[String, Table], limitTo: Option[(Table, Int, Int)]): SpanQuery = {
    val gs = GeneralizedQuerySampler.buildGeneralizedSpanQuery(
      tokenizedQuery,
      searcher, tables, posSampleSize, limitTo
    )
    if (tokenizedQuery.size < maxEdits) {
      new SpanQueryMinimumValidCaptures(gs, maxEdits, tokenizedQuery.getNames)
    } else {
      gs
    }
  }

  override def getSample(qexpr: TokenizedQuery, searcher: Searcher,
    targetTable: Table, tables: Map[String, Table]): Hits = {
    searcher.find(buildGeneralizingQuery(qexpr, searcher, tables, None))
  }

  override def getLabelledSample(
    qexpr: TokenizedQuery,
    searcher: Searcher,
    targetTable: Table,
    tables: Map[String, Table],
    startFromDoc: Int,
    startFromToken: Int
  ): Hits = {
    val spanQuery = buildGeneralizingQuery(
      qexpr, searcher, tables, Some((targetTable, startFromDoc, startFromToken))
    )
    searcher.find(spanQuery)
  }
}
