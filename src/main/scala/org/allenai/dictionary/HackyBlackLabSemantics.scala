package org.allenai.dictionary

import nl.inl.blacklab.search.{ TextPattern, TextPatternProperty, TextPatternTerm }
import nl.inl.blacklab.search.sequences.TextPatternSequence
import org.allenai.common.immutable.Interval

// TODO: https://github.com/allenai/okcorpus/issues/31
object HackyBlackLabSemantics {
  /** For some reason, blacklab hates matching disjunctions at the first token position. This
    * hack updates the query to include a constant match against any token at the prefix of the
    * query. This means that you won't be able to match the first token of the first sentence in
    * a document, but it gets around the annoying blacklab bug.
    */
  def blackLabQuery(qexpr: QExpr): TextPattern = {
    val query = BlackLabSemantics.blackLabQuery(qexpr)
    val const = new TextPatternProperty("const", new TextPatternTerm("1"))
    return new TextPatternSequence(const, query)
  }
  /** Function that updates the match index to not include the constant term to the left. */
  def removeConstToken(result: BlackLabResult): BlackLabResult = {
    val matchOffset = result.matchOffset
    val fixed = Interval.open(matchOffset.start + 1, matchOffset.end)
    result.copy(matchOffset = fixed)
  }
}
