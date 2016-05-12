package org.allenai.ike

import org.allenai.common.Logging

import scala.collection.GenTraversableOnce
import scala.collection.immutable.Iterable

/** Implement this trait for expanding (generalizing) tables with seed entries.
  * Various similarity measures may be used. Each can be implemented as a separate TableExpander.
  */
trait TableExpander {
  def expandTableColumn(table: Table, columnName: String): Seq[SimilarPhrase]
}

/** Class that generalizes a given table (column) entries using similar phrases.
  * The basic idea here is:
  * expand each seed row in the given column using SimilarPhrasesSearcher, then determine / return
  * the intersection set (phrases returned as matches for a "large" fraction of rows in the table--
  * hard-coded to 75%.
  * Uses SimilarPhrasesSearcher internally to expand each table entry.
  * @param similarPhrasesSearcher
  */
class SimilarPhrasesBasedIntersectionTableExpander(similarPhrasesSearcher: SimilarPhrasesSearcher)
    extends Logging with TableExpander {

  override def expandTableColumn(table: Table, columnName: String): Seq[SimilarPhrase] = {
    // Get index of the required column in the table.
    val colIndex = table.getIndexOfColumn(columnName)

    // Helper Method to compute arithmetic mean similarity score.
    def averageSimilarity(similarityScores: Seq[Double]): Double = {
      val numPhrases = similarityScores.length
      if (numPhrases > 0) {
        similarityScores.sum / numPhrases
      } else {
        0.0
      }
    }

    val currentTableRows = table.positive.map(_.values(colIndex))

    // Construct a map of all similar phrases retrieved with corresponding scores.
    // Filter those that do not occur at least (75% number of rows) times --
    // a good chance these were found in the similar phrase sets for 75% of the rows,
    // assuming word2vec doesn't return duplicates in getSimilarPhrases query.
    // Score is arithmetic mean of all similarity scores obtained.
    val phraseScoreMap = (currentTableRows.flatMap {
      tableEntry =>
        similarPhrasesSearcher.getSimilarPhrases(tableEntry.qwords.map(_.value).mkString(" "))
    }).groupBy(_.qwords)
      .filter(_._2.size >= 0.75 * currentTableRows.size)
      .mapValues(phrases => averageSimilarity(phrases.map(_.similarity)))

    // Convert the phrase score map into SimilarPhrase objects and return.
    phraseScoreMap.map(x => new SimilarPhrase(x._1, x._2)).toSeq
  }
}

/** Class that generalizes a given table (column) entries using SimilarPhrasesSearcher. The basic
  * idea here is: get the phrases similar to the set of all seed entries, by using
  * the logic implemented in SimilarPhrasesSearcher.getSimilarPhrases
  * @param similarPhrasesSearcher
  */
class SimilarPhrasesBasedTableExpander(similarPhrasesSearcher: SimilarPhrasesSearcher)
    extends Logging with TableExpander {

  override def expandTableColumn(table: Table, columnName: String): Seq[SimilarPhrase] = {
    // Get index of the required column in the table.
    val colIndex = table.getIndexOfColumn(columnName)
    // Construct set of all table rows.
    val currentTableEntries = new scala.collection.mutable.HashSet[Seq[QWord]]()

    // Retrieve the set of entries in the particular column being expanded.
    val columnEntries = for {
      row <- table.positive
    } yield {
      val tableEntry = row.values(colIndex)
      currentTableEntries.add(tableEntry.qwords)
      tableEntry.qwords.map(_.value).mkString(" ")
    }

    val expandedSet = similarPhrasesSearcher.getSimilarPhrases(columnEntries)

    // If the table entries are missing from the similar phrase-set, they should be added to the
    // set. i.e. ExpandedSet should be a superset of original set.
    for (entry: String <- columnEntries) {
      if (!expandedSet.contains(entry)) {
        expandedSet.+:(SimilarPhrase(entry.split("_").map(QWord), 1))
      }
    }
    return expandedSet
  }
}
