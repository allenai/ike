package org.allenai.dictionary

import org.allenai.common.Logging

import scala.collection.GenTraversableOnce
import scala.collection.immutable.Iterable

/** Implement this trait for expanding (generalizing) tables with seed entries.
  * Various similarity measures may be used. Each can be implemented as a separate TableExpander.
  */
trait TableExpander {
  def expandTableColumn(table: Table, columnName: String): Seq[SimilarPhrase]
}

/** Class that generalizes a given table (column) entries using Word2Vec. The basic idea here is:
  * expand each seed row in the given column using Word2Vec, then determine / return the
  * intersection set (phrases returned as matches for a "large" fraction of rows in the table--
  * hard-coded to 75%.
  * Uses WordVecPhraseSearcher internally to expand each table entry.
  * @param wordvecSearcher
  */
class WordVecIntersectionTableExpander(wordvecSearcher: WordVecPhraseSearcher)
    extends Logging with TableExpander {

  override def expandTableColumn(table: Table, columnName: String): Seq[SimilarPhrase] = {
    // Get index of the required column in the table.
    val colIndex = table.getIndexOfColumn(columnName)

    // Helper Method to compute arithmetic mean similarity score.
    def averageSimilarity(similarityScores: Seq[Double]): Double = {
      val numPhrases = similarityScores.length
      if (numPhrases > 0) {
        similarityScores.sum / numPhrases
      } else 0.0
    }

    val currentTableRows = table.positive.map(_.values(colIndex))

    // Construct a map of all similar phrases retrieved with corresponding scores.
    // Filter those that do not occur at least (75% number of rows) times --
    // a good chance these were found in the similar phrase sets for 75% of the rows,
    // assuming word2vec doesn't return duplicates in getSimilarPhrases query.
    // Score is arithmetic mean of all similarity scores obtained.
    val phraseScoreMap = (currentTableRows.flatMap {
      tableEntry =>
        wordvecSearcher.getSimilarPhrases(tableEntry.qwords.map(_.value).mkString(" "))
    }).groupBy(_.qwords)
      .filter(_._2.size >= 0.75 * currentTableRows.size)
      .mapValues(phrases => averageSimilarity(phrases.map(_.similarity)))

    // Convert the phrase score map into SimilarPhrase objects and return.
    phraseScoreMap.map(x => new SimilarPhrase(x._1, x._2)).toSeq
  }
}

/** Class that generalizes a given table (column) entries using Word2Vec. The basic idea here is:
  * get the word2vec centroid of all seed entries, then return the neighbors of the centroid.
  * @param wordvecSearcher
  */
class WordVecCentroidTableExpander(wordvecSearcher: WordVecPhraseSearcher)
    extends Logging with TableExpander {

  override def expandTableColumn(table: Table, columnName: String): Seq[SimilarPhrase] = {
    // Get index of the required column in the table.
    val colIndex = table.getIndexOfColumn(columnName)

    // Construct set of all table rows. If the same entries appear in the similar phrases result
    // returned by the WordVecPhraseSearcher, they should be filtered out.
    val currentTableEntries = new scala.collection.mutable.HashSet[Seq[QWord]]()

    val columnEntries = for {
      row <- table.positive
    } yield {
      val tableEntry = row.values(colIndex)
      currentTableEntries.add(tableEntry.qwords)
      tableEntry.qwords.map(_.value).mkString(" ")
    }
    wordvecSearcher.getCentroidMatches(columnEntries) filter (
      x => !currentTableEntries.contains(x.qwords)
    )
  }
}
