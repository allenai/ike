package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.{ Hits, Searcher }
import org.allenai.dictionary._
import org.allenai.dictionary.ml.{ CapturedTokenSequence, TokenSequence, TokenizedQuery }

object MatchesSampler {

  /** Returns a QExpr that is matches tokenizedQuery except any individual queryTokens
    * that are of variable length are wrapped in capture groups
    */
  def getNamedQuery(tokenizedQuery: TokenizedQuery): QExpr = {
    QSeq(tokenizedQuery.getSequencesWithNames.flatMap {
      case (tseq, names) =>
        val tokensWithName = tseq.queryTokens.zip(names).map {
          case (qexpr, name) =>
            val (min, max) = QueryLanguage.getQueryLength(qexpr)
            if (min == max) {
              qexpr
            } else {
              QNamed(qexpr, name)
            }
        }
        tseq match {
          case CapturedTokenSequence(_, name, _) =>
            Seq(QNamed(TokenizedQuery.qexprFromSequence(tokensWithName), name))
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
    tables: Map[String, Table]
  ): Hits = {
    val query = QueryLanguage.interpolateTables(MatchesSampler.getNamedQuery(qexpr), tables).get
    searcher.find(BlackLabSemantics.blackLabQuery(query))
  }

  override def getLabelledSample(
    qexpr: TokenizedQuery,
    searcher: Searcher,
    targetTable: Table,
    tables: Map[String, Table],
    startFromDoc: Int,
    startFromToken: Int
  ): Hits = {
    val query = QueryLanguage.interpolateTables(MatchesSampler.getNamedQuery(qexpr), tables).get
    val spanQuery = searcher.createSpanQuery(BlackLabSemantics.blackLabQuery(query))
    val tableQuery = Sampler.buildLabelledQuery(qexpr, targetTable)
    val tableSpanQuery = searcher.createSpanQuery(BlackLabSemantics.blackLabQuery(tableQuery))
    val filteredQuery = new SpanQueryFilterByCaptureGroups(spanQuery, tableSpanQuery,
      targetTable.cols, startFromDoc, startFromToken)
    searcher.find(filteredQuery)
  }
}
