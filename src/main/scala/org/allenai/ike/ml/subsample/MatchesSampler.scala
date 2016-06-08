package org.allenai.ike.ml.subsample

import org.allenai.ike._
import org.allenai.ike.ml._
import org.allenai.ike.patterns.NamedPattern

import org.allenai.blacklab.search.{ Hits, Searcher }

object MatchesSampler {

  /** Returns a query that matches the same hits as `tokenSequence` but where any query-token that
    * could match a variable number of tokens is wrapped in a capture group drawn from the
    * corresponding entry in `name`
    */
  def captureQueryTokens(queryTokenSequence: QueryTokenSequence, names: Seq[String]): Seq[QExpr] = {
    queryTokenSequence.queryTokens.zip(names).map {
      case (qexpr, name) =>
        val (min, max) = QueryLanguage.getQueryLength(qexpr)
        if (min == max) {
          qexpr
        } else {
          QNamed(qexpr, name)
        }
    }
  }

  /** Returns a QExpr that matches tokenizedQuery and where any individual query-tokens
    * that are of variable length are wrapped in capture groups
    */
  def getNamedQuery(tokenizedQuery: TokenizedQuery): QExpr = {
    QSeq(tokenizedQuery.getSequencesWithNames.flatMap {
      case (tseq, names) =>
        val tokensWithName = captureQueryTokens(tseq, names)
        tseq match {
          case CapturedTokenSequence(_, name, _) =>
            Seq(QNamed(TokenizedQuery.qexprFromSequence(tokensWithName), name))
          case TokenSequence(_) => tokensWithName
        }
    })
  }

  /** For a one column table, returns a Query similar to `getNamedQuery`,
    * but already limited to the rows in single column table `table`
    */
  def getNamedColumnMatchingQuery(tokenizedQuery: TokenizedQuery, table: Table): QExpr = {
    // We can optimize this case by ANDing the capture group with a query matching the table rows
    // instead of using SpanQueryFilterByCaptureGroups
    require(table.cols.size == 1)
    val filteredRows = Sampler.getFilteredRows(tokenizedQuery, table)
    val captureQuery = QDisj(filteredRows.map(x => QSeq(x.head)))
    QSeq(tokenizedQuery.getSequencesWithNames.flatMap {
      case (tseq, names) =>
        val tokensWithName = captureQueryTokens(tseq, names)
        tseq match {
          case CapturedTokenSequence(_, name, _) =>
            val captureAndQuery =
              QAnd(TokenizedQuery.qexprFromSequence(tokensWithName), captureQuery)
            Seq(QNamed(captureAndQuery, name))
          case TokenSequence(_) => tokensWithName
        }
    })
  }
}

/** Samples hits that the given query already matches
  */
case class MatchesSampler() extends Sampler() {
  override def getSample(
    qexpr: TokenizedQuery,
    searcher: Searcher,
    targetTable: Table,
    tables: Map[String, Table],
    patterns: Map[String, NamedPattern]
  ): Hits = {
    val query = QueryLanguage.interpolateTables(
      MatchesSampler.getNamedQuery(qexpr),
      tables,
      patterns,
      None
    ).get
    searcher.find(BlackLabSemantics.blackLabQuery(query))
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
    val spanQuery = if (targetTable.cols.size == 1) {
      val oneColQexpr = MatchesSampler.getNamedColumnMatchingQuery(qexpr, targetTable)
      val interQuery = QueryLanguage.interpolateTables(oneColQexpr, tables, patterns, None).get
      val spanQuery = searcher.createSpanQuery(BlackLabSemantics.blackLabQuery(interQuery))
      new SpanQueryStartAt(spanQuery, startFromDoc, startFromToken)
    } else {
      val query = QueryLanguage.interpolateTables(
        MatchesSampler.getNamedQuery(qexpr), tables, patterns, None
      ).get
      val spanQuery = searcher.createSpanQuery(BlackLabSemantics.blackLabQuery(query))
      val tableQuery = Sampler.buildLabelledQuery(qexpr, targetTable)
      val tableSpanQuery = searcher.createSpanQuery(BlackLabSemantics.blackLabQuery(tableQuery))
      new SpanQueryFilterByCaptureGroups(spanQuery, tableSpanQuery,
        targetTable.cols, startFromDoc, startFromToken)
    }
    searcher.find(spanQuery)
  }
}
