package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.lucene._
import org.apache.lucene.index.{ AtomicReaderContext, IndexReader, Term, TermContext }
import org.apache.lucene.search.Query
import org.apache.lucene.search.spans.{ SpanQuery, Spans }
import org.apache.lucene.util.Bits

/** Gathers 'Fuzzy' matches to a sequence. In other words matches sequences within
  * <code>minMatches</code> to <code>maxMatches</code> inclusive edit distance of the input
  * sequence of clauses, where an edit is changing a single token within the sequence
  * (removing or adding tokens is not allowed currently). Input clauses must be fixed length.
  * Input clauses can Integers, in which case they are treated as a sequence of WildCards with the
  * given size.
  * See [[org.allenai.dictionary.ml.subsample.SpansFuzzySequence]]
  *
  * For example, if our clause matches "a", "cat", and "ran" with minMatches=2 maxMatches=3
  * this matches sentences like:
  * "the cat ran"
  * "a dog ran"
  * "a cat walked"
  * but not:
  * "the dog ran"
  *
  * If minMatches=2 maxMatches=2 this matches:
  * "a cat walked"
  * "the dog ran"
  * but not:
  * "a cat ran"
  * "the dog walked"
  *
  * @param mixedClauses Sequence of Queries or integers to use as subclauses
  * @param minMatches Minimum number of clauses that must match a sequence for us to return that
  *                sequence
  * @param maxMatches Maximum number of clauses that must match a sequence we return
  * @param captureMisses Whether to return, the clauses that did not participate in a match, the
  *                   spans of where they should have matched
  * @param ignoreLastToken Whether to ignore the last token of each document
  * @param sequencesToCapture Subsequences of each match to return as capture groups
  */
class SpanQueryFuzzySequence(
  mixedClauses: Seq[Either[SpanQuery, Int]],
  minMatches: Int,
  maxMatches: Int,
  captureMisses: Boolean,
  ignoreLastToken: Boolean,
  sequencesToCapture: Seq[CaptureSpan]
) extends SpanQueryBase(
  mixedClauses.flatMap {
  case Left(query) => Some(query)
  case _ => None
}.toArray
) {

  def this(
    mixedClauses: => Seq[SpanQuery], // use "=>" to avoid conflicting w/previous constructor
    minMatches: Int,
    maxMatches: Int,
    captureMisses: Boolean,
    ignoreLastToken: Boolean,
    sequencesToCapture: Seq[CaptureSpan]
  ) = {
    this(mixedClauses map (Left(_)), minMatches, maxMatches, captureMisses,
      ignoreLastToken, sequencesToCapture)
  }

  override def rewrite(reader: IndexReader): Query = {
    /* Rewrite queries to allow wildcards or 'matchAll' spans to implicitly match everything by
     * replacing them with implicit matches */

    def isWildCard(query: Query): Boolean = {
      query match {
        case s: SpanQueryNot =>
          val matchAll = SpanQueryNot.matchAllTokens(ignoreLastToken, s.getField)
          // Sorry about this, SpanQueryNot does not implement equals as expected,
          // nor does it expose the number of clauses it contains so
          // so we are left with this hack to check if it matches all tokens
          matchAll.toString.equals(s.toString())
        case _ => false
      }
    }

    val rewrittenClauses = mixedClauses.map {
      case Left(q: Query) =>
        val rewritten = q.rewrite(reader) match {
          case sq: SpanQuery => sq
          case _ => throw new RuntimeException("SpanQuery should always rewrite as a SpanQuery")
        }
        if (isWildCard(rewritten)) {
          Right(1)
        } else {
          Left(rewritten)
        }
      case r: Right[SpanQuery, Int] => r
    }
    new SpanQueryFuzzySequence(rewrittenClauses, minMatches, maxMatches, captureMisses,
      ignoreLastToken, sequencesToCapture)
  }

  override def getSpans(
    atomicReaderContext: AtomicReaderContext,
    bits: Bits,
    map: java.util.Map[Term, TermContext]
  ): Spans = {
    val baseSpans: Seq[Either[BLSpans, Int]] = mixedClauses map {
      case Left(spans: SpanQuery) => Left(BLSpansWrapper.optWrap(
        spans.getSpans(atomicReaderContext, bits, map)
      ))
      case Right(n: Int) => Right(n)
    }
    val lengthGetter = new DocFieldLengthGetter(atomicReaderContext.reader(), getField)

    // We need to always ensure at least one explicit clause matches, otherwise we
    // will match everything
    val adjustedMinMatches = Math.max(1, minMatches)
    new SpansFuzzySequence(baseSpans, lengthGetter,
      adjustedMinMatches, maxMatches, true, ignoreLastToken, sequencesToCapture, captureMisses)
  }

  override def toString(s: String): String = {
    s"<${clauses.map(_.toString).mkString(",")}>~{$minMatches-$maxMatches}"
  }
}
