package org.allenai.dictionary.ml

import nl.inl.blacklab.search.Searcher
import org.allenai.dictionary.{ SuggestQueryConfig, Table, QExpr }

case class ScoredQuery(query: QExpr, score: Double, msg: String)

object QuerySuggester {

  def suggestQuery(
    searcher: Searcher,
    startingQuery: QExpr,
    tables: Map[String, Table],
    target: String,
    narrow: Boolean,
    config: SuggestQueryConfig
  ): Seq[ScoredQuery] = ???
}

