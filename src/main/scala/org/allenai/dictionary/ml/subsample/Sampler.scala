package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.{ Hits, Searcher }
import org.allenai.dictionary._

/** Abstract class for classes the subsample data from a corpus using a black lab Searcher.
  * Classes return hits from the corpus that are 'close' to being matched by an input query, where
  * 'close' is defined by the subclass.
  */
abstract class Sampler {

  /** Gets a random sample of hits from a corpus that are close to a query.
    *
    * @param qexpr Query to sample for
    * @param searcher Searcher to get samples from
    * @return Hits object containing the samples
    */
  def getSample(qexpr: QExpr, searcher: Searcher): Hits

  /** Gets a sample of hits from a corpus that are 'close' to a given query, and that are 
    * also limited to hits that capture terms from a particular table.
    *
    * @param qexpr Query to sample for
    * @param searcher Searcher to get samples from
    * @param table Table to limit queries to
    * @return Hits object containing the samples
    */
  def getLabelledSample(qexpr: QExpr, searcher: Searcher, table: Table): Hits
}

