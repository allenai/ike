package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.{ Hits, Searcher }
import org.allenai.dictionary._
import org.allenai.dictionary.ml.TokenizedQuery

/** Samples hits that the given query already matches
  */
case class MatchesSampler() extends Sampler() {

  override def getSample(
    qexpr: TokenizedQuery,
    searcher: Searcher,
    targetTable: Table,
    tables: Map[String, Table]
  ): Hits = {
    val query = QueryLanguage.interpolateTables(qexpr.getNamedQuery, tables).get
    searcher.find(BlackLabSemantics.blackLabQuery(query))
  }

  override def getLabelledSample(
    qexpr: TokenizedQuery,
    searcher: Searcher,
    targetTable: Table,
    tables: Map[String, Table],
    startFromDoc: Int
  ): Hits = {
    val rowQuery = Sampler.buildLabelledQuery(qexpr, targetTable)
    val query = QueryLanguage.interpolateTables(qexpr.getNamedQuery, tables).get
    val spanQuery = searcher.createSpanQuery(BlackLabSemantics.blackLabQuery(query))
    val rowSpanQuery = searcher.createSpanQuery(BlackLabSemantics.blackLabQuery(rowQuery))
    val filteredQuery = new SpanQueryFilterByCaptureGroups(spanQuery, rowSpanQuery,
      targetTable.cols, startFromDoc)
    searcher.find(filteredQuery)
  }
}
