package org.allenai.dictionary.ml

import org.allenai.common.{ Logging, Timing }
import org.allenai.dictionary.{ QueryLanguage, QWord, Table }
import org.allenai.dictionary.ml.queryop.{ OpGenerator, QueryOp }

import nl.inl.blacklab.search.Hits

import scala.collection.immutable.IntMap
import scala.collection.JavaConverters._

object Label extends Enumeration {
  type Label = Value
  val Positive, Negative, Unlabelled = Value
}
import Label._

/** A token within a sentence and some of its annotations */
case class Token(word: String, pos: String)

/** A sequence of tokens that are associated with a QExpr, if didMatch is true the QExpr matched
  * these tokens, if didMatch is false the QExpr did not match these tokens, but it needs to for the
  * query as a whole to match the sentence the tokens are in. This sequence can be zero length if
  * the QExpr did not match any tokens, or needed to match zero tokens (if didMatch is true)
  */
case class QueryMatch(tokens: Seq[Token], didMatch: Boolean)

/** A QExpr, assumed to be one token of a TokenizedQuery, and the tokens that individual QExpr
  * matched within a sequence of sentences
  *
  * @param queryToken The query-token
  * @param matches the Tokens this QExpr matched, or should have matched, within a sequence of
  *           sentences the original TokenizedQuery matched, or nearly matched
  */
case class QueryMatches(
  queryToken: QuerySlotData,
  matches: Seq[QueryMatch]
)

/** Cached analysis for a number of hits, includes the labels for each subsample and which
  * sentences each primitive operation would allow the starting query to match
  *
  * @param operatorHits Maps primitive operations to map of (sentence index within Examples ->
  *              number of required 'edit' that operator completes for that sentence
  *              (can be 0)). If a sentence index is not included the starting query will no long
  *              match the corresponding sentence if the query operaiton is applied to it
  * @param examples List of examples, one for each sentence
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
  * query would apply to
  */
object HitAnalyzer extends Logging {

  /** Builds a list of Example objects, one for each hit in Hits
    *
    * @param hits, Hits to build examples from
    * @param table, Table to use when deciding each Hit's label
    * @return The examples
    */
  def getExamples(query: TokenizedQuery, hits: Hits, table: Table): Iterable[Example] = {
    var positiveStrings = table.positive.seq.map(_.values.map(
      _.qwords.map(_.value.toLowerCase)
    )).toSet
    val negativeStrings = table.negative.seq.map(_.values.map(
      _.qwords.map(_.value.toLowerCase)
    )).toSet

    // If the whole query is QWords, delete the encoded word from the
    // dictionary so its occurances will be treated as unlabelled
    val columnToQSeq = query.captures.map(x => (x.columnName, x.seq)).toMap
    val captureGroupsInOrder = table.cols.map(columnToQSeq(_))
    if (captureGroupsInOrder.forall(_.forall(_.isInstanceOf[QWord]))) {
      val encodedSeq = captureGroupsInOrder.map(_.map(_.asInstanceOf[QWord].value.toLowerCase))
      positiveStrings = positiveStrings - encodedSeq
      logger.debug(s"Removed ${encodedSeq.map(_.mkString(" ")).mkString("::")} from " +
        s"positive examples to avoid it being suggested")
    }

    hits.get(0) // Makes sure captureGroupNames has been loaded
    val captureNames = hits.getCapturedGroupNames
    val captureGroupIndices = table.cols.map(captureNames.indexOf(_))
    val otherCaptureIndices = (Range(0, captureNames.size).toSet -- captureGroupIndices.toSet).toSeq
    assert(
      captureGroupIndices.forall(_ >= 0),
      s"Expected capture names ${table.cols} but got $captureNames"
    )
    hits.asScala.map { hit =>
      val allCaptureSpans = hits.getCapturedGroups(hit)
      val captureSpans = captureGroupIndices.map(allCaptureSpans(_))
      val kwic = hits.getKwic(hit)
      val (label, captures) = {
        val shift = hit.start - kwic.getHitStart
        val captures = captureSpans.map {
          captureSpan =>
            if (captureSpan == null) {
              // We assume null captures --> imply the capture group was in an empty
              // sequences, which can occur for some queries like "(.*) b"
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
        span != null && allCaptureSpans(index).end < 0
      })
      val str = kwic.getTokens("word").asScala.mkString(" ")
      Example(label, requiredEdits, captures, hit.doc, str)
    }
  }

  /** Builds a sequence of WeightedExamples from a set of Examples. Currently we down weight
    * examples that captured the same string to encourage returning queries that would return a
    * diversity of different sentences.
    */
  def getWeightedExamples(examples: Seq[Example]): IndexedSeq[WeightedExample] = {
    val counts = examples.map(example => example.captureStrings).
      groupBy(identity).mapValues(x => x.size)
    examples.map { example =>
      val numOfSameStrings = counts(example.captureStrings).toDouble
      WeightedExample(example.label, example.requiredEdits,
        math.sqrt(numOfSameStrings) / numOfSameStrings,
        example.doc, example.str)
    }.toIndexedSeq
  }

  /** Build a sequence of QueryMatches for the given Hits object and query. For token in the given
    * TokenizedQuery, a QueryMatches object will be built that shows what that token matched, or
    * should have matched, for each Hit in Hits. QueryMatches objects will also be built for each
    * prefix and suffix slot as specified by prefixCounts and suffixCounts. Hits that returned a
    * null capture group should be skipped.
    */
  def getMatchData(
    hits: Hits,
    query: TokenizedQuery,
    prefixCounts: Int,
    suffixCounts: Int
  ): Seq[QueryMatches] = {

    // Set up our hits object, call .get(0) so it initializes captureGroupsNames
    val props = List("word", "pos")
    hits.setContextField(props.asJava)
    hits.setContextSize(math.max(prefixCounts, suffixCounts))
    hits.get(0)

    val captureGroups = hits.getCapturedGroupNames

    // For each queryToken, Either the capture group that token's matches are stored
    // in or the number of tokens that queryToken will have match
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
    val allQueryMatches = hits.asScala.map { hit =>
      val kwic = hits.getKwic(hit)
      val captureGroups = hits.getCapturedGroups(hit)
      val hitQueryMatches = {
        var tokens = props.map(prop => kwic.getMatch(prop).asScala).
          transpose.map(x => Token(x(0), x(1)))
        queryTokenMatchLocations.map {
          case Right(captureGroupIndex) =>
            val span = captureGroups(captureGroupIndex)
            val length = if (span == null) {
                0
              } else {
                math.abs(span.end) - math.abs(span.start)
              }
            val (matchedTokens, rest) = tokens.splitAt(length)
            tokens = rest
            QueryMatch(matchedTokens, didMatch = span != null && span.end >= 0)
          case Left(numTokens) =>
              val (matchedTokens, rest) = tokens.splitAt(numTokens)
              tokens = rest
              QueryMatch(matchedTokens, didMatch = true)
        }
      }

      val prefixQueryMatches = {
        val prefixTokens = props.map(prop => kwic.getLeft(prop).asScala).transpose.
          reverse.take(prefixCounts).map(x =>
            QueryMatch(Seq(Token(x(0), x(1))), didMatch = true))
        prefixTokens.toSeq.padTo(prefixCounts, QueryMatch(Seq(), didMatch = true)).reverse
      }

      val suffixQueryMatches = {
        // == "" to filter out blank ending tokens BlackLab sometime inserts
        val suffixTokens = props.map(prop => kwic.getRight(prop).asScala).transpose.
          take(suffixCounts).filter(x => x.forall(_ != "")).map(
            x => QueryMatch(Seq(Token(x(0), x(1))), didMatch = true)
          )
        suffixTokens.toSeq.padTo(suffixCounts, QueryMatch(Seq(), didMatch = true))
      }

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
    if (allQueryMatches.isEmpty) {
      // No hits found, so zip the metadata with empty sequences
      expressions.map(QueryMatches(_, Seq()))
    } else {
      // Zip it with the matches for each slot
      allQueryMatches.transpose.zip(expressions).map {
        case (matches, slotData) =>
          QueryMatches(slotData, matches.toSeq)
      }.toSeq
    }
  }

  /** Builds a HitAnalysis object
    *
    * @param hits sequence of hits to build the object from
    * @param query Query to use when deciding which primitive operations to generate
    * @param prefixCounts number of tokens before each to gather and pass to generator
    *                  in a Prefix Slot.
    * @param suffixCounts number of tokens before each hit to gather and pass to generator
    *                  in a Suffix Slot.
    * @param generator generator used to decide what primtive operations to generate for each
    *               query-token within query
    * @param table table to use when labelling the hits as positive or negative
    * @return the HitAnalysis object containing one 'Example' for each hit in hits, in the same
    *      order as was given
    */
  def buildHitAnalysis(
    hits: Seq[Hits],
    query: TokenizedQuery,
    prefixCounts: Int,
    suffixCounts: Int,
    generator: OpGenerator,
    table: Table
  ): HitAnalysis = {

    val props = List("word", "pos")
    hits.foreach(_.setContextField(props.asJava))
    hits.foreach(_.setContextSize(math.max(prefixCounts, suffixCounts)))

    logger.debug("Calculating labels and weights...")
    val (examples, exampleTime) = Timing.time {
      val examples = hits.map(h =>
        getExamples(query, h, table)).flatten
      getWeightedExamples(examples)

    }
    logger.debug(s"Done in ${exampleTime.toMillis / 1000.0} seconds")

    logger.debug("Calculating match data...")
    val (opMap, opMaptime) = Timing.time {
      val matchData = hits.map(h => getMatchData(h, query, prefixCounts, suffixCounts))
      val opMaps = matchData.transpose.map { matchList =>
        assert(matchList.forall(_.queryToken == matchList.head.queryToken))
        QueryMatches(matchList.head.queryToken, matchList.map(_.matches).flatten)
      }
      opMaps.foldLeft(Map[QueryOp, IntMap[Int]]()) {
        case (acc, data) =>
          val newOps = generator.generate(data)
          assert(acc.keySet.intersect(newOps.keySet).size == 0)
          acc ++ newOps
      }
    }
    logger.debug(s"Got match data in ${opMaptime.toMillis / 1000.0} seconds")
    HitAnalysis(opMap, examples)
  }
}
