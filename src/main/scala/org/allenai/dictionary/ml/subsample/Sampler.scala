package org.allenai.dictionary.ml.subsample

import org.allenai.common.Logging
import org.allenai.dictionary.ml.TokenizedQuery

import nl.inl.blacklab.search.{Hits, Searcher}
import org.allenai.dictionary._

object Sampler {

  def getLabelledExampleQuery(query: TokenizedQuery, table: Table): QExpr = {
    val captureSizes = query.captures.map(x => QueryLanguage.getQueryLength(x.getQuery))
    captureSizes.foreach { size => require(size > 0) }
    val allRows = (table.positive ++ table.negative).map(x => x.values)
    val asWords = allRows.map(r => r.seq.map(v => v.qwords))
    // Filter rows where one of the values in the row is of the wrong length
    val filteredWords = asWords.filter(captureSeq => {
      captureSeq.zip(captureSizes).forall {
        case (words, captureSize) => words.size == captureSize
      }
    })
    val distanceBetweenCaptures = query.nonCaptures.map(x =>
      QueryLanguage.getQueryLength(QSeq(x)))
    val asSequences = filteredWords.map(row => {
      // For each row, build a query of the form
      // ". . phraseInColumn1 . . . phraseInColumn2 .. phraseInColumn2 . ."
      val withWildCards = row.zip(distanceBetweenCaptures).map {
        case (seq, distance) => List.tabulate(distance)(_ => QWildcard()) ++ seq
      } :+ List.tabulate(distanceBetweenCaptures.last)(_ => QWildcard())
      QSeq(withWildCards.flatten)
    })
    QDisj(asSequences)
  }

  /** Returns a modified query expression whose capture groups are limited
    * to matching entities in a table.
    *
    * @throws IllegalArgumentException if there were not positive rows of the right length
    *                                  to filter capture groups of qexpr with
    */
  def limitQueryToTable(query: TokenizedQuery, table: Table): QExpr = {
    QAnd(query.getQuery, getLabelledExampleQuery(query, table))
  }
}


/** Abstract class for classes the subsample data from a corpus using a black lab Searcher.
  * Classes return hits from the corpus that are 'close' to being matched by an input query, where
  * 'close' is defined by the subclass.
  */
abstract class Sampler extends Logging {

  /** Gets a random sample of hits from a corpus that are close to a query.
    *
    * @param qexpr Query to sample for
    * @param searcher Searcher to get samples from
    * @param table Table the query is targeting
    * @return Hits object containing the samples
    */
  def getSample(qexpr: QExpr, searcher: Searcher, table: Table): Hits

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

