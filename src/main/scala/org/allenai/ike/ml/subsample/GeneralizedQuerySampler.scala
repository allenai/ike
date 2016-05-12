package org.allenai.ike.ml.subsample

import org.allenai.ike._
import org.allenai.ike.ml._
import org.allenai.ike.patterns.NamedPattern

import nl.inl.blacklab.search._
import nl.inl.blacklab.search.sequences.TextPatternSequence
import org.apache.lucene.search.spans.SpanQuery

object GeneralizedQuerySampler {

  /** Builds a SpanQuery that matches the generalized version of `tokenizedQuery`. If `limitTo`
    * is defined it contains (table, startDoc, startToken) and the returned query will only match
    * rows from table and will only return Hits after startDoc and startToken
    */
  def buildGeneralizedSpanQuery(
    tokenizedQuery: TokenizedQuery,
    searcher: Searcher,
    tables: Map[String, Table],
    patterns: Map[String, NamedPattern],
    posSampleSize: Int,
    limitTo: Option[(Table, Int, Int)]
  ): SpanQuery = {
    def toTextPattern(qexpr: QExpr): TextPattern = {
      BlackLabSemantics.blackLabQuery(QueryLanguage.interpolateTables(qexpr, tables, patterns, None).get)
    }
    def phrase2QExpr(phrase: Seq[QWord]): QExpr = {
      if (phrase.size == 1) phrase.head else QSeq(phrase)
    }
    val generalizations = tokenizedQuery.generalizations.get

    val oneCaptureGroup =
      tokenizedQuery.tokenSequences.count(_.isInstanceOf[CapturedTokenSequence]) == 1

    // Build span queries for each query/generalization
    val generalizingSpanQueries = generalizations.zip(tokenizedQuery.getNamedTokens).map {
      case (GeneralizeToDisj(qpos, qsimiliar, _), (name, original)) =>
        val originalSq = toTextPattern(original)
        // 'Flatten' the qSimQueries into individual QExpr, this gives us the chance to filter
        // out repeats and minimize nesting of Disjunction queries
        val qSimQueries = qsimiliar.flatMap { qsim =>
          phrase2QExpr(qsim.qwords) +: qsim.phrases.map(sp => phrase2QExpr(sp.qwords))
        }
        val extensions = (qpos ++ qSimQueries).distinct.map(toTextPattern)
        new TextPatternTrackingDisjunction(originalSq, extensions, name)
      // Currently we do not handle GeneralizeToAll
      case (_, (name, original)) => toTextPattern(QNamed(original, name))
    }

    // Group the span queries into chunks and wrap the right chunks in capture groups
    var remaining = generalizingSpanQueries
    var chunked = List[TextPattern]()
    tokenizedQuery.tokenSequences.foreach { ts =>
      val (chunk, rest) = remaining.splitAt(ts.size)
      remaining = rest
      val next = ts match {
        case CapturedTokenSequence(_, name, _) =>
          if (oneCaptureGroup && limitTo.isDefined) {
            // In this case use TextPatternAnd to make sure this capture group must match tokens
            // with in the given table
            val filteredRows = Sampler.getFilteredRows(tokenizedQuery, limitTo.get._1)
            val tableTextPattern = toTextPattern(QDisj(filteredRows.map(x => QSeq(x.head))))
            Seq(new TextPatternCaptureGroup(new TextPatternAnd(
              new TextPatternSequence(chunk: _*), tableTextPattern
            ), name))
          } else {
            Seq(new TextPatternCaptureGroup(new TextPatternSequence(chunk: _*), name))
          }
        case TokenSequence(_) => chunk
      }
      chunked = chunked ++ next
    }
    assert(remaining.isEmpty)
    val spanQuery = searcher.createSpanQuery(new TextPatternSequence(chunked: _*))
    limitTo match {
      case Some((_, doc, token)) =>
        if (oneCaptureGroup) {
          // We already limited to the query by ANDing it when building `chunked`
          new SpanQueryStartAt(spanQuery, doc, token)
        } else {
          // Otherwise we have to use the slower SpanQueryFilterByCaptureGroups
          val captureGroups = tokenizedQuery.tokenSequences.flatMap {
            case cts: CapturedTokenSequence => Some(cts.captureName)
            case _ => None
          }
          val tableQexpr = Sampler.buildLabelledQuery(tokenizedQuery, limitTo.get._1)
          val tableTextPattern = BlackLabSemantics.blackLabQuery(tableQexpr)
          val tableSpanQuery = searcher.createSpanQuery(tableTextPattern)
          new SpanQueryFilterByCaptureGroups(spanQuery, tableSpanQuery, captureGroups, doc, token)
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

  def buildGeneralizingQuery(
    tokenizedQuery: TokenizedQuery,
    searcher: Searcher,
    tables: Map[String, Table],
    patterns: Map[String, NamedPattern],
    limitTo: Option[(Table, Int, Int)]
  ): SpanQuery = {
    val gs = GeneralizedQuerySampler.buildGeneralizedSpanQuery(
      tokenizedQuery,
      searcher,
      tables,
      patterns,
      posSampleSize,
      limitTo
    )
    if (tokenizedQuery.size < maxEdits) {
      new SpanQueryMinimumValidCaptures(gs, maxEdits, tokenizedQuery.getNames)
    } else {
      gs
    }
  }

  override def getSample(
    qexpr: TokenizedQuery,
    searcher: Searcher,
    targetTable: Table,
    tables: Map[String, Table],
    patterns: Map[String, NamedPattern]
  ): Hits = {
    searcher.find(buildGeneralizingQuery(qexpr, searcher, tables, patterns, None))
  }

  override def getLabelledSample(
    qexpr: TokenizedQuery,
    searcher: Searcher,
    targetTable: Table,
    tables: Map[String, Table],
    patterns: Map[String, NamedPattern],
    startFromDoc: Int,
    startFromToken: Int
  ): Hits = {
    val spanQuery = buildGeneralizingQuery(
      qexpr, searcher, tables, patterns, Some((targetTable, startFromDoc, startFromToken))
    )
    searcher.find(spanQuery)
  }
}
