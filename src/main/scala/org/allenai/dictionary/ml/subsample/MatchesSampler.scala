package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.{ Hits, Searcher }
import org.allenai.common.Logging
import org.allenai.dictionary._

/** Samples hits that the given query already matches.
  */
case class MatchesSampler() extends Sampler() with Logging {

  override def getSample(qexpr: QExpr, searcher: Searcher, table: Table): Hits = {
    searcher.find(BlackLabSemantics.blackLabQuery(qexpr))
  }

  override def getLabelledSample(qexpr: QExpr, searcher: Searcher, table: Table): Hits = {
    val limitedQuery = Sampler.limitQueryToTable(qexpr, table)
    searcher.find(BlackLabSemantics.blackLabQuery(limitedQuery))
  }
}
