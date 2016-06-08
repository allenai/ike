package org.allenai.ike.ml.subsample

import org.allenai.common.Logging
import org.allenai.ike._
import org.allenai.ike.ml.{ CapturedTokenSequence, TokenizedQuery }
import org.allenai.ike.patterns.NamedPattern

import org.allenai.blacklab.search.{ Hits, Searcher }

object Sampler extends Logging {

  /** @return the rows within a table that the given query might match, rows are returned as a
    * sequence of phrases, each phrase is a sequence of QWords
    */
  def getFilteredRows(query: TokenizedQuery, table: Table): Seq[Seq[Seq[QWord]]] = {
    val captureSizes = query.getCaptureGroups.map {
      case (colName, seq) =>
        val querySize = QueryLanguage.getQueryLength(QSeq(seq))
        colName -> querySize
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

  /** @param query QExpr the returned query is being built for
    * @param table to build the query for
    * @return QExpr that captures rows of the given table that `query` might capture
    */
  def buildLabelledQuery(
    query: TokenizedQuery,
    table: Table
  ): QExpr = {
    val filteredRows = getFilteredRows(query, table)

    require(filteredRows.nonEmpty, "Query could not match any rows")

    // Reorder rows to match the query
    val colNameToColumn = table.cols.zip(filteredRows.transpose).toMap
    val captureNames = query.getCaptureGroups.map(_._1)
    val rowsReordered = captureNames.map(colNameToColumn(_)).transpose

    var prevWasCapture = query.tokenSequences.head.isInstanceOf[CapturedTokenSequence]
    val distanceBetweenCaptures = query.tokenSequences.drop(1).flatMap { tokenSequence =>
      val distance = if (tokenSequence.isInstanceOf[CapturedTokenSequence] && !prevWasCapture) {
        Some(QueryLanguage.getQueryLength(tokenSequence.getOriginalQuery))
      } else {
        None
      }
      prevWasCapture = tokenSequence.isInstanceOf[CapturedTokenSequence]
      distance
    }
    val asSequences = rowsReordered.map(row => {
      val rowCaptures = row.zip(captureNames).map {
        case (phrase, columnName) => QNamed(QSeq(phrase), columnName)
      }
      // For each row, build a query of the form
      // "phraseInColumn1 . . . phraseInColumn2 .* phraseInColumn2"
      val withWildCards = distanceBetweenCaptures.zip(rowCaptures).map {
        case ((min, max), capture) => Seq(capture, QRepetition(QWildcard(), min, max))
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
  * subclasses can choose to not register it in the capture groups. This can make some queries more
  * efficient
  */
abstract class Sampler {

  /** Gets a random sample of hits from a corpus that are close to a query
    *
    * @param qexpr Query to sample for
    * @param searcher Searcher to get samples from
    * @param targetTable Table the query is targeting
    * @param tables map of string->Table, used for interpolating queries
    * @param patterns map of string->NamedPattern, used for interpolating queries
    * @return Hits object containing the samples
    */
  def getSample(
    qexpr: TokenizedQuery,
    searcher: Searcher,
    targetTable: Table,
    tables: Map[String, Table],
    patterns: Map[String, NamedPattern]
  ): Hits

  /** Gets a sample of hits from a corpus that are 'close' to a given query, and that are
    * also limited to hits that capture terms from a particular table
    *
    * @param qexpr Query to sample for
    * @param searcher Searcher to get samples from
    * @param targetTable Table to limit queries to
    * @param tables map of string->Table, used for interpolating queries
    * @param patterns map of string->NamedPattern, used for interpolating queries
    * @param startFromDoc document to start collecting hits from, returned hits will not have doc
    *                smaller than startFromDoc
    * @return Hits object containing the samples
    */
  def getLabelledSample(
    qexpr: TokenizedQuery,
    searcher: Searcher,
    targetTable: Table,
    tables: Map[String, Table],
    patterns: Map[String, NamedPattern],
    startFromDoc: Int,
    startFromToken: Int
  ): Hits
}

