package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.{ Hits, Searcher }
import org.allenai.dictionary._
import org.allenai.dictionary.ml.TokenizedQuery
import org.apache.lucene.search.spans.SpanQuery

/** Sampler that returns sentences that could be matched by a query that is
  * within an edit distance of the given query. See SpanQueryFuzzySequence
  *
  * @param minEdits minimum edits a sentence can be from the query to be returned
  * @param maxEdits maximum edits a sentence can be from the query to be returned
  */
case class FuzzySequenceSampler(minEdits: Int, maxEdits: Int)
    extends Sampler() {

  require(minEdits >= 0)
  require(maxEdits >= minEdits)

  def buildFuzzySequenceQuery(tokenizedQuery: TokenizedQuery, searcher: Searcher,
    tables: Map[String, Table]): SpanQuery = {
    require(QueryLanguage.getQueryLength(tokenizedQuery.getQuery)._2 > 0)
    val asSpanQueries = tokenizedQuery.getSeq.map { queryToken =>
      val query = QueryLanguage.interpolateTables(queryToken, tables).get
      searcher.createSpanQuery(BlackLabSemantics.blackLabQuery(query))
    }

    // Figure out what subsequence we should record as capture group.
    var onIndex = 0
    var captureList = List[CaptureSpan]()
    tokenizedQuery.nonCaptures.zip(tokenizedQuery.captures).foreach {
      case (leftNonCapture, capture) =>
        onIndex += leftNonCapture.size
        captureList = CaptureSpan(capture.columnName, onIndex,
          onIndex + capture.seq.size) +: captureList
        onIndex += capture.seq.size
    }
    val querySize = tokenizedQuery.size
    new SpanQueryFuzzySequence(asSpanQueries, querySize - maxEdits, querySize - minEdits, true,
      searcher.getIndexStructure.alwaysHasClosingToken(), captureList)
  }

  override def getSample(qexpr: TokenizedQuery, searcher: Searcher,
    targetTable: Table, tables: Map[String, Table]): Hits = {
    searcher.find(buildFuzzySequenceQuery(qexpr, searcher, tables))
  }

  override def getLabelledSample(
    qexpr: TokenizedQuery,
    searcher: Searcher,
    targetTable: Table,
    tables: Map[String, Table],
    startFromDoc: Int,
    startFromToken: Int
  ): Hits = {
    val rowQuery = Sampler.buildLabelledQuery(qexpr, targetTable)
    val sequenceQuery = buildFuzzySequenceQuery(qexpr, searcher, tables)
    val rowSpanQuery = searcher.createSpanQuery(BlackLabSemantics.blackLabQuery(rowQuery))
    searcher.find(new SpanQueryFilterByCaptureGroups(sequenceQuery, rowSpanQuery,
      targetTable.cols, startFromDoc, startFromToken))
  }
}
