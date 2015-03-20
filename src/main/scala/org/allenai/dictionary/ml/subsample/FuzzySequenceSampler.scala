package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.lucene.SpanQueryAnd
import nl.inl.blacklab.search.{ Hits, Searcher }
import org.allenai.dictionary._
import org.allenai.dictionary.ml.TokenizedQuery
import org.apache.lucene.search.spans.SpanQuery

object FuzzySequenceSampler {
  val captureGroupName = "capture"
}
/** Sampler that returns sentences that could be matched by a query that is
  * within an edit distance of the given query. See SpansFuzzySequence.
  *
  * @param minEdits minimum edits a sentence can be from the query to be returned
  * @param maxEdits maximum edits a sentence can be from the query to be returned
  */
case class FuzzySequenceSampler(minEdits: Int, maxEdits: Int)
    extends Sampler() {

  require(minEdits >= 0)
  require(maxEdits >= minEdits)

  def buildFuzzySequenceQuery(tokenizedQuery: TokenizedQuery, searcher: Searcher): SpanQuery = {
    val asSpanQueries = tokenizedQuery.getSeq.map(
      q => searcher.createSpanQuery(BlackLabSemantics.blackLabQuery(q))
    )
    val captureGroup = CaptureSpan(FuzzySequenceSampler.captureGroupName, tokenizedQuery.left.size,
      tokenizedQuery.left.size + tokenizedQuery.capture.size)
    val querySize = tokenizedQuery.getSeq.size
    new SpanQueryFuzzySequence(asSpanQueries, querySize - maxEdits, querySize - minEdits, true,
      searcher.getIndexStructure.alwaysHasClosingToken(),
      Seq(captureGroup))
  }

  override def getRandomSample(qexpr: QExpr, searcher: Searcher): Hits = {
    val tokenizedQuery = TokenizedQuery.buildFromQuery(qexpr)
    searcher.find(buildFuzzySequenceQuery(tokenizedQuery, searcher))
  }

  override def getLabelledSample(qexpr: QExpr, searcher: Searcher, table: Table): Hits = {
    require(table.cols.size == 1)
    val tokenizedQuery = TokenizedQuery.buildFromQuery(qexpr)
    val query = buildFuzzySequenceQuery(tokenizedQuery, searcher)
    val captureLength = QueryLanguage.getQueryLength(QSeq(tokenizedQuery.capture))
    val precapture = QueryLanguage.getQueryLength(QSeq(tokenizedQuery.left))
    val postcapture = QueryLanguage.getQueryLength(QSeq(tokenizedQuery.right))
    val allRows = table.positive ++ table.negative
    val allWords = allRows.map(x => x.values.head.qwords)
    val patternsToUse = allWords.filter(x => x.size == captureLength).map(x => QSeq(x))
    println(s"Limiting query to ${patternsToUse.size} dictionary patterns of size $captureLength")
    val limitingQexpr = QSeq((List.tabulate(precapture)(_ =>
      QWildcard()) :+ QDisj(patternsToUse)) ++ List.tabulate(postcapture)(_ => QWildcard()))
    val limitingTextPattern = BlackLabSemantics.blackLabQuery(limitingQexpr)
    val limitingQuery = searcher.createSpanQuery(limitingTextPattern)
    searcher.find(new SpanQueryAnd(query, limitingQuery))
  }
}
