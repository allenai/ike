package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.{Hits, Searcher}
import org.allenai.dictionary._

/**
 * Abstract class for classes the subsample data from a corpus of data
 * using a Searcher.
 */
abstract class Sampler {

  /**
   * Gets a random sample of hits from a corpus that are 'close' to the
   * given query.
   *
   * @param qexpr Query to sample for
   * @param searcher Searcher to get samples from
   * @return Hits object containing the samples
   */
  def getRandomSample(qexpr: QExpr, searcher: Searcher): Hits

  /**
   * Gets a random sample of hits from a corpus that are 'close' to the
   * given query, and that are also limited to hits that capture
   * elements from a given table.
   *
   * @param qexpr Query to sample for
   * @param searcher Searcher to get samples from
   * @param table Table to limit queries to
   * @return Hits object containing the samples
   */
  def getLabelledSample(qexpr: QExpr, searcher: Searcher, table: Table): Hits
}


