package org.allenai.dictionary.ml.subsample

import org.allenai.common.Logging
import org.allenai.dictionary.ml.{ CaptureSequence, TokenizedQuery }

import nl.inl.blacklab.search.{ Hits, Searcher }
import org.allenai.dictionary._

import org.apache.lucene.search.spans.SpanQuery

object Sampler extends Logging {

  /** @return the rows within a table that the given query might match, rows are returned as a
    *   sequence of phrases, each phrase is a sequence of QWords
    */
  def getFilteredRows(query: TokenizedQuery, table: Table): Seq[Seq[Seq[QWord]]] = {
    val captureSizes = query.captures.map {
      captureSequence =>
        val querySize = QueryLanguage.getQueryLength(captureSequence.getQuery)
        (captureSequence.columnName -> querySize)
    }.toMap
    val orderedCapturedSizes = table.cols.map(captureSizes(_))

    // Filter rows from the query that cannot match the query
    val allRows = (table.positive ++ table.negative).map(_.values.map(_.qwords))
    allRows.filter { row =>
      row.zip(orderedCapturedSizes).forall {
        case (words, captureSize) => words.size >= captureSize._1 &&
          (captureSize._2 == -1 || words.size <= captureSize._2)
      }
    }
  }

  /** @param query Query the labelled query will be used to filter
    * @param table to build the query for
    * @return QExpr that captures rows of the given table
    */
  def buildLabelledQuery(
    query: TokenizedQuery,
    table: Table
  ): QExpr = {
    val filteredRows = getFilteredRows(query, table)

    require(filteredRows.size > 0)

    // Reorder row to match the query
    val colNameToColumn = table.cols.zip(filteredRows.transpose).toMap
    val captureNames = query.captures.map(_.columnName)
    val rowsReordered = captureNames.map(colNameToColumn(_)).transpose

    val distanceBetweenCaptures = query.nonCaptures.drop(1).dropRight(1).
      map(x => QueryLanguage.getQueryLength(QSeq(x)))

    val asSequences = rowsReordered.map(row => {
      val rowCaptures = row.zip(captureNames).map {
        case (phrase, columnName) => QNamed(QSeq(phrase), columnName)
      }
      // TODO we could also limit the starting query to only match a length that could match a row
      // For each row, build a query of the form
      // "phraseInColumn1 . . . phraseInColumn2 .* phraseInColumn2"
      val withWildCards = distanceBetweenCaptures.zip(rowCaptures).map {
        case (distance, capture) => Seq(capture, QRepetition(QWildcard(), distance._1, distance._2))
      } :+ List(rowCaptures.last)
      QSeq(withWildCards.flatten)
    })
    QDisj(asSequences)
  }
}

/** Abstract class for classes the subsample data from a corpus using a black lab Searcher.
  * Classes return hits from the corpus that are 'close' to being matched by an input query, where
  * 'close' is defined by the subclass. If a query matches a sentence in multiple ways it is
  * allowed to return the same capture span multiple times (ex. a* cat might return "a a cat" and
  * "a cat" for the sentence "a a cat".
  *
  * The returned hits are additionally expected to be annotated so we can recover which parts of
  * the tokenized query matched which parts of each return hit. This is accomplished by
  * registering capture groups for each query-token in TokenizedQuery that captures which words
  * within each hit that query-token matched. In some cases a query-token might have not matched
  * any tokens within the hit (meaning the query as a whole will not have matched the hit), in
  * this case that query-token's capture group should indicate which part of the hit the
  * query-token would have needed to match for the overall query to match the hit, only with the
  * start and end values negated.
  *
  * Finally, if a query-token is fixed length and will never fail to match some part of the hit,
  * subclasses can not register it in the capture groups. This can make some queries more efficient
  */
abstract class Sampler {

  /** Gets a random sample of hits from a corpus that are close to a query
    *
    * @param qexpr Query to sample for
    * @param searcher Searcher to get samples from
    * @param targetTable Table the query is targeting
    * @param tables map of string->Table, used for interpolating queries
    * @return Hits object containing the samples
    */
  def getSample(qexpr: TokenizedQuery, searcher: Searcher, targetTable: Table,
    tables: Map[String, Table]): Hits

  /** Gets a sample of hits from a corpus that are 'close' to a given query, and that are
    * also limited to hits that capture terms from a particular table
    *
    * @param qexpr Query to sample for
    * @param searcher Searcher to get samples from
    * @param targetTable Table to limit queries to
    * @param tables map of string->Table, used for interpolating queries
    * @param startFromDoc document to start collecting hits from, returned hits will not have doc
    *                 smaller than startFromDoc
    * @return Hits object containing the samples
    */
  def getLabelledSample(
    qexpr: TokenizedQuery,
    searcher: Searcher,
    targetTable: Table,
    tables: Map[String, Table],
    startFromDoc: Int,
    startFromToken: Int
  ): Hits
}

