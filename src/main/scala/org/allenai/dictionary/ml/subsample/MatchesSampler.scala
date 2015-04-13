package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.{ Hits, Searcher }
import org.allenai.dictionary._
import org.allenai.dictionary.ml.TokenizedQuery

/** Samples hits that the given query already matches.
  */
case class MatchesSampler() extends Sampler() {

  override def getSample(qexpr: TokenizedQuery, searcher: Searcher, table: Table): Hits = {
    searcher.find(BlackLabSemantics.blackLabQuery(qexpr.getNamedQuery))
  }

  override def getLabelledSample(
    qexpr: TokenizedQuery,
    searcher: Searcher,
    table: Table,
    startFromDoc: Int
  ): Hits = {
    val rowQuery = Sampler.buildLabelledQuery(qexpr, table)
    val spanQuery = searcher.createSpanQuery(
      BlackLabSemantics.blackLabQuery(qexpr.getNamedQuery)
    )
    val rowSpanQuery = searcher.createSpanQuery(BlackLabSemantics.blackLabQuery(rowQuery))
    val filteredQuery = new SpanQueryFilterByCaptureGroups(spanQuery, rowSpanQuery,
      table.cols, startFromDoc)
    searcher.find(filteredQuery)
  }
}
