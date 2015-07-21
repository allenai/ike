package org.allenai.dictionary.ml

import nl.inl.blacklab.search.Hits
import org.allenai.common.{ Logging, Timing }
import org.allenai.dictionary.ml.queryop.{ OpGenerator, QueryOp }
import org.allenai.dictionary.{ QueryLanguage, Table }

import scala.collection.JavaConverters._
import scala.collection.immutable.IntMap

object Label extends Enumeration {
  type Label = Value
  val Positive, Negative, Unlabelled = Value
}
import org.allenai.dictionary.ml.Label._

/** A token within a sentence and some of its annotations */
case class Token(word: String, pos: String)

/** A sequence of tokens that are associated with a QExpr, if didMatch is true the QExpr matched
  * these tokens, if didMatch is false the QExpr did not match these tokens, but it needs to for the
  * query as a whole to match the sentence the tokens are in. This sequence can be zero length if
  * the QExpr did not match any tokens, or needed to match zero tokens and didMatch is true
  */
case class QueryMatch(tokens: Seq[Token], didMatch: Boolean)

/** A QExpr, assumed to be one token of a TokenizedQuery, and the tokens that individual QExpr
  * matched within a sequence of sentences
  *
  * @param queryToken The QExpr and accompanying meta-data
  * @param matches the Tokens this QExpr matched, or should have matched, within a sequence of
  *        sentences the original TokenizedQuery matched, or nearly matched
  */
case class QueryMatches(
  queryToken: QuerySlotData,
  matches: Seq[QueryMatch]
)

/** Cached analysis for a number of Hit objects produced by a Sampler, includes the labels
  * for each Hit and, for a number of QueryOps, how applying that QueryOp to
  * the QExpr that generated those Hits would alter which Hits that QExpr would continue to match.
  *
  * @param operatorHits Maps primitive operations to IntMaps, which map (sentence index within
  *           Examples) -> (number of required 'edits' that operator completes for that sentence
  *           (can be 0)). If a sentence index is not included in the map it implies the starting
  *           query will no longer match the corresponding sentence if that query operation is
  *           applied to it
  * @param examples List of examples, one for each Hit
  */
case class HitAnalysis(
    operatorHits: Map[QueryOp, IntMap[Int]],
    examples: IndexedSeq[WeightedExample]
) {
  def size: Int = examples.size
}

/** Contains the logic needed to turn BlackLab Hits into a HitAnalysis object, this
  * involves parsing this Hits to determine the labels of the hits, and to determine what
  * QueryOps can be applied, and how applying those ops would alter which hits the starting
  * query would match
  */
object HitAnalyzer extends Logging {

  /** Builds a list of Example objects, one for each Hit in Hits
    *
    * @param hits Hits to build examples from, should not be empty
    * @param table Table to use when deciding each Hit's label
    * @return The examples
    */
  def getExamples(query: TokenizedQuery, hits: Hits, table: Table): Iterable[Example] = {
    require(hits.sizeAtLeast(1))
    val positiveStrings = table.positive.seq.map(_.values.map(
      _.qwords.map(_.value.toLowerCase)
    )).toSet
    val negativeStrings = table.negative.seq.map(_.values.map(
      _.qwords.map(_.value.toLowerCase)
    )).toSet

    hits.get(0) // Makes sure captureGroupNames has been loaded
    val captureNames = hits.getCapturedGroupNames
    val captureGroupIndices = table.cols.map(captureNames.indexOf(_))
    val otherCaptureIndices = (Range(0, captureNames.size).toSet -- captureGroupIndices.toSet).toSeq
    assert(
      captureGroupIndices.forall(_ >= 0),
      s"Expected capture names ${table.cols} but got $captureNames"
    )
    hits.asScala.map { hit =>
      if (Thread.interrupted()) throw new InterruptedException()
      val allCaptureSpans = hits.getCapturedGroups(hit)
      val columnsCaptures = captureGroupIndices.map(allCaptureSpans(_))
      val kwic = hits.getKwic(hit)
      val (label, captures) = {
        val shift = hit.start - kwic.getHitStart
        val captures = columnsCaptures.map {
          captureSpan =>
            if (captureSpan == null) {
              // We assume null captures imply the capture group captured nothing,
              // which can occur for some queries like "(.*) b"
              Seq()
            } else {
              kwic.getTokens("word").subList(
                captureSpan.start - shift,
                captureSpan.end - shift
              ).asScala.map(_.toLowerCase)
            }
        }
        val label = if (positiveStrings contains captures) {
          Positive
        } else if (negativeStrings contains captures) {
          Negative
        } else {
          Unlabelled
        }
        (label, captures)
      }
      val requiredEdits = otherCaptureIndices.count(index => {
        val span = allCaptureSpans(index)
        // We expect the Sampler that produced these hits to have marked Spans within each Hit
        // that the starting query did not match exactly with a negated Span object
        span != null && allCaptureSpans(index).end < 0
      })
      val str = kwic.getTokens("word").asScala.mkString(" ")
      Example(label, requiredEdits, captures, hit.doc, str)
    }
  }

  /** Builds a sequence of WeightedExamples from a set of Examples. Currently we down weight
    * examples that captured the same string to encourage returning queries that would return a
    * diversity of captured output.
    */
  def getWeightedExamples(examples: Seq[Example]): IndexedSeq[WeightedExample] = {
    val phraseToIndex = examples.map(_.captureStrings).distinct.zipWithIndex.toMap
    val phraseToCounts = examples.map(_.captureStrings).groupBy(identity).mapValues(_.size)

    examples.map { example =>
      val numOfSameStrings = phraseToCounts(example.captureStrings).toDouble
      val phraseId = phraseToIndex(example.captureStrings)
      WeightedExample(example.label, phraseId, example.requiredEdits,
        example.doc, math.sqrt(numOfSameStrings) / numOfSameStrings, example.str)
    }.toIndexedSeq
  }

  /** Build a sequence of QueryMatches for the given non-empty Hits object and query. For token in
    * the given TokenizedQuery, a QueryMatches object will be built that shows what that token
    * matched, or should have matched, for each Hit in Hits. QueryMatches objects will also be
    * built for each prefix and suffix slot as specified by prefixCounts and suffixCounts.
    */
  def getMatchData(
    hits: Hits,
    query: TokenizedQuery,
    prefixCounts: Int,
    suffixCounts: Int
  ): Seq[QueryMatches] = {
    require(hits.sizeAtLeast(1))

    // Set up our hits object, call .get(0) so it initializes captureGroupsNames
    val props = List("word", "pos")
    hits.setContextSize(math.max(prefixCounts, suffixCounts))
    hits.setForwardIndexConcordanceParameters("word", null, List("pos").asJava)
    hits.get(0)

    val captureGroups = hits.getCapturedGroupNames

    // For each queryToken within `query`, queryTokenMatchLocations stores either the capture group
    // index where that token's spans are saved in or the number of tokens that queryToken most
    // have matched
    val queryTokenMatchLocations = query.getNamedTokens.map {
      case (name, token) =>
        if (captureGroups.contains(name)) {
          Right(captureGroups.indexOf(name))
        } else {
          val size = QueryLanguage.getQueryLength(token)
          require(size._2 != -1 && size._1 == size._2)
          Left(size._2)
        }
    }

    // For each Hit in Hits, break that hit into a sequence of QueryMatches
    val queryMatchesPerHit = hits.asScala.map { hit =>
      val kwic = hits.getKwic(hit)
      val captureGroups = hits.getCapturedGroups(hit)
      val hitQueryMatches = {
        var tokens = props.map(prop => kwic.getMatch(prop).asScala).
          transpose.map(x => Token(x(0).toLowerCase, x(1)))
        queryTokenMatchLocations.map { tokensMatches =>
          val qMatch = tokensMatches match {
            case Right(captureGroupIndex) =>
              val span = captureGroups(captureGroupIndex)
              val length = if (span == null) {
                0
              } else {
                // math.abs so we get the span length right for captured Spans that were
                // negated by the Sampler that built these Hits
                math.abs(span.end - span.start)
              }
              val (matchedTokens, rest) = tokens.splitAt(length)
              tokens = rest
              QueryMatch(matchedTokens, didMatch = span != null && span.end >= 0)
            case Left(numTokens) =>
              val (matchedTokens, rest) = tokens.splitAt(numTokens)
              tokens = rest
              QueryMatch(matchedTokens, didMatch = true)
          }
          if (Thread.interrupted()) throw new InterruptedException()
          qMatch
        }
      }

      val prefixQueryMatches = {
        val prefixTokens = props.map(prop => kwic.getLeft(prop).asScala).transpose.
          reverse.take(prefixCounts).map(x =>
            QueryMatch(Seq(Token(x(0).toLowerCase, x(1))), didMatch = true))
        prefixTokens.toSeq.padTo(prefixCounts, QueryMatch(Seq(), didMatch = true)).reverse
      }
      if (Thread.interrupted()) throw new InterruptedException()

      val suffixQueryMatches = {
        // != "" to filter out blank ending tokens BlackLab sometimes inserts
        val suffixTokens = props.map(prop => kwic.getRight(prop).asScala).transpose.
          take(suffixCounts).filterNot(_.contains("")).map(
            x => QueryMatch(Seq(Token(x(0).toLowerCase, x(1))), didMatch = true)
          )
        suffixTokens.toSeq.padTo(suffixCounts, QueryMatch(Seq(), didMatch = true))
      }
      if (Thread.interrupted()) throw new InterruptedException()

      assert(hitQueryMatches.size == query.size)
      prefixQueryMatches ++ hitQueryMatches ++ suffixQueryMatches
    }

    // Collect the meta-data for each Slot
    val prefixExpressions = List.tabulate[QuerySlotData](prefixCounts) {
      i => QuerySlotData(Prefix(prefixCounts - i))
    }
    val matchExpressions = query.getAnnotatedSeq
    val suffixExpressions = List.tabulate[QuerySlotData](suffixCounts) {
      i => QuerySlotData(Suffix(i + 1))
    }
    val expressions = prefixExpressions ++ matchExpressions ++ suffixExpressions

    if (queryMatchesPerHit.isEmpty) {
      // No hits found, so zip the metadata with empty sequences
      expressions.map(QueryMatches(_, Seq()))
    } else {
      // Zip the meta-data with the matches we found (transposed so we have a list of QueryMatches
      // for each Slot)
      queryMatchesPerHit.transpose.zip(expressions).map {
        case (matches, slotData) =>
          QueryMatches(slotData, matches.toSeq)
      }.toSeq
    }
  }

  /** Builds a HitAnalysis object
    *
    * @param hits sequence of hits to build the object from
    * @param query Query to use when deciding which primitive operations to generate
    * @param prefixCounts number of tokens before each hit to gather and pass to generator
    *               in a Prefix Slot.
    * @param suffixCounts number of tokens after each hit to gather and pass to generator
    *               in a Suffix Slot.
    * @param generator generator used to decide what primitive operations to generate for each
    *            query-token within query
    * @param table table to use when labelling the hits as positive or negative
    * @return the HitAnalysis object containing one 'Example' for each hit in hits, in the same
    *   order as was given
    */
  def buildHitAnalysis(
    hits: Seq[Hits],
    query: TokenizedQuery,
    prefixCounts: Int,
    suffixCounts: Int,
    generator: OpGenerator,
    table: Table
  ): HitAnalysis = {

    val nonEmptyHits = hits.filter(_.size() > 0)

    nonEmptyHits.foreach(_.setForwardIndexConcordanceParameters("word", null, List("pos").asJava))
    nonEmptyHits.foreach(_.setContextSize(math.max(prefixCounts, suffixCounts)))

    logger.debug("Calculating labels and weights...")
    val (examples, exampleTime) = Timing.time {
      val unweightedExamples = nonEmptyHits.flatMap(getExamples(query, _, table))
      getWeightedExamples(unweightedExamples)
    }
    logger.debug(s"Done in ${exampleTime.toMillis / 1000.0} seconds")

    logger.debug("Calculating match data...")
    val (opMap, opMaptime) = Timing.time {
      val matchDataPerHit = nonEmptyHits.par.map(
        getMatchData(_, query, prefixCounts, suffixCounts)
      ).seq
      val matchDataPerSlot = matchDataPerHit.transpose.map { slotMatchData =>
        assert(slotMatchData.forall(_.queryToken == slotMatchData.head.queryToken))
        QueryMatches(slotMatchData.head.queryToken, slotMatchData.flatMap(_.matches))
      }
      matchDataPerSlot.foldLeft(Map[QueryOp, IntMap[Int]]()) {
        case (accumulatedOpMap, queryMatches) =>
          val newOps = generator.generate(queryMatches, examples)
          assert(accumulatedOpMap.keySet.intersect(newOps.keySet).isEmpty)
          accumulatedOpMap ++ newOps
      }
    }
    logger.debug(s"Got match data in ${opMaptime.toMillis / 1000.0} seconds")
    HitAnalysis(opMap, examples)
  }
}
