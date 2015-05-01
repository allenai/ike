package org.allenai.dictionary.ml

import org.allenai.dictionary.ml.HitAnalyzer._
import org.allenai.dictionary.ml.Label._
import org.allenai.dictionary.ml.compoundop._
import org.allenai.dictionary.ml.queryop._

import nl.inl.blacklab.search._
import org.allenai.common.{ Logging, Timing }
import org.allenai.dictionary._
import org.allenai.dictionary.ml.subsample._
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.collection.immutable.IntMap

case class Suggestions(
  original: ScoredQuery,
  suggestions: Seq[ScoredQuery],
  docsSampledFrom: Int
)
case class ScoredOps(ops: CompoundQueryOp, score: Double, positiveScore: Double,
  negativeScore: Double, unlabelledScore: Double)
case class ScoredQuery(query: QExpr, score: Double, positiveScore: Double,
  negativeScore: Double, unlabelledScore: Double)

/** 'Hit' in the corpus we will evaluate candidate queries on
  *
  * @param label label of the hit
  * @param requiredEdits number of query-tokens we need to edit for the starting query to match
  *          this hit (see the ml/README.md)
  * @param captureStrings the string we captured, as a Sequence of capture groups of sequences of
  *           words
  * @param doc the document number this Example came from
  * @param str String of hit, kept only for debugging purposes
  */
case class Example(label: Label, requiredEdits: Int,
  captureStrings: Seq[Seq[String]], doc: Int, str: String = "")

/** Example, but with an associated weight indicating how important it is to get this example
  * correct
  */
case class WeightedExample(label: Label, requiredEdits: Int,
  weight: Double, doc: Int, str: String = "")

object QuerySuggester extends Logging {

  lazy val querySuggestionConf = ConfigFactory.load().getConfig("QuerySuggester")

  /** Uses a beam search to select the best CompoundQueryOp
    *
    * @param hitAnalysis data to use when evaluating queries
    * @param evaluationFunction function to evaluate CompoundQueryOps with
    * @param opBuilder Builder function for CompoundQueryOps
    * @param beamSize Size of the beam to use in the search
    * @param depth Depth to run the search to, corresponds the to maximum size
    * of a CompoundQueryOp that can be proposed
    * @param query optional query, only used when printing query ops
    * @return Sequence of CompoundQueryOps, together with their score and a string
    * message about some statistics about that op of at most beamSize size
    */
  def selectOperator(
    hitAnalysis: HitAnalysis,
    evaluationFunction: QueryEvaluator,
    opBuilder: EvaluatedOp => Option[CompoundQueryOp],
    beamSize: Int,
    depth: Int,
    query: Option[TokenizedQuery] = None
  ): Seq[(CompoundQueryOp, Double)] = {

    // Reverse ordering so the smallest scoring operators are at the head
    val priorityQueue = scala.collection.mutable.PriorityQueue()(
      Ordering.by[(CompoundQueryOp, Double), Double](_._2)
    ).reverse

    // Add length1 operators to the queue
    hitAnalysis.operatorHits.foreach {
      case (operator, matches) =>
        val op = opBuilder(EvaluatedOp(operator, matches))
        if (op.isDefined) {
          val score = evaluationFunction.evaluate(op.get, 1)
          if (priorityQueue.size < beamSize) {
            priorityQueue.enqueue((op.get, score))
          } else if (priorityQueue.head._2 < score) {
            priorityQueue.dequeue()
            priorityQueue.enqueue((op.get, score))
          }
        }
    }

    def printQueue(depth: Int): Unit = {
      priorityQueue.foreach {
        case (op, score) =>
          val opStr = if (query.isEmpty) op.toString() else op.toString(query.get)
          logger.debug(s"$opStr\n${evaluationFunction.evaluationMsg(op, depth)}")
      }
    }

    val alreadyFound = scala.collection.mutable.Set[Set[TokenQueryOp]]()

    // Start the beam search, note this search follows a breadth-first pattern where we keep a fixed
    // number of nodes at each depth rather then the more typical depth-first style of beam search.
    for (i <- 1 until depth) {
      logger.debug(s"********* Depth $i *********")
      printQueue(i + 1)

      if (evaluationFunction.usesDepth()) {
        // Now the depth has changed, rescore the old queries
        val withNewScores = priorityQueue.map {
          case (x, _) => (x, evaluationFunction.evaluate(x, i + 1))
        }
        priorityQueue.clear()
        withNewScores.foreach(x => priorityQueue.enqueue(x))
      }

      // Expand new nodes by trying to AND with every possible base operator
      priorityQueue.filter {
        // Filter out ops that are from a lower depth iteration since we have already tried
        // expanding them. Note this assumes that the children of this node, when re-evaluated at
        // a lower depth, will not have a higher scores then they did previously.
        case (operator, score) => operator.size == i
      }.to[Seq].foreach { // iterate over a copy so we do not re-expand nodes
        case (operator, score) =>
          hitAnalysis.operatorHits.
            // Filter our unusable operators
            filter { case (newOp, opMatches) => operator.canAdd(newOp) }.
            // Map the remaining operators to new compound rules
            foreach {
              case (newOp, opMatches) =>
                val newOperator = operator.add(EvaluatedOp(newOp, opMatches))
                if (!alreadyFound.contains(newOperator.ops)) {
                  val newScore = evaluationFunction.evaluate(newOperator, 1 + i)
                  if (priorityQueue.size < beamSize) {
                    priorityQueue.enqueue((newOperator, newScore))
                  } else if (newScore > priorityQueue.head._2) {
                    priorityQueue.dequeue()
                    priorityQueue.enqueue((newOperator, newScore))
                  }
                  alreadyFound.add(newOperator.ops)
                }
            }
      }
    }

    logger.debug("******* DONE ************")
    printQueue(depth)
    priorityQueue.dequeueAll.reverse.toSeq
  }

  /** Return a set of suggested queries given a starting query and a Table
    *
    * @param searcher Searcher to the corpus to use when suggesting new queries
    * @param startingQuery Starting query to build suggestion from
    * @param tables Tables to use when building the query
    * @param target Name of the table to optimize the suggested queries for
    * @param narrow Whether the suggestions should narrow or broaden the starting
    * query
    * @param config Configuration details to use when suggesting the new queries
    * @return Suggested queries, along with their scores and a String msg details some
    * statistics about each query
    */
  def suggestQuery(
    searcher: Searcher,
    startingQuery: QExpr,
    tables: Map[String, Table],
    target: String,
    narrow: Boolean,
    config: SuggestQueryConfig
  ): Suggestions = {
    val percentUnlabelled = querySuggestionConf.getDouble("percentUnlabelled")
    val targetTable = tables.get(target) match {
      case Some(table) => table
      case None => throw new IllegalArgumentException("Target table not found")
    }
    require(QueryLanguage.getCaptureGroups(startingQuery).size == targetTable.cols.size)

    val queryWithNamedCaptures = QueryLanguage.nameCaptureGroups(startingQuery, targetTable.cols)
    val tokenizedQuery = TokenizedQuery.buildFromQuery(queryWithNamedCaptures)

    logger.info(s"Making ${if (narrow) "narrowing" else "broadening"} " +
      s"suggestion for <${QueryLanguage.getQueryString(queryWithNamedCaptures)}> for $target")
    logger.info(s"Configuration: $config")

    val hitGatherer = if (narrow) {
      MatchesSampler()
    } else {
      FuzzySequenceSampler(1, Math.min(tokenizedQuery.size - 1, config.depth))
    }

    logger.debug("Reading unlabelled hits")
    val ((unlabelledHits, unlabelledSize), unlabelledReadTime) = Timing.time {
      val hits = hitGatherer.getSample(tokenizedQuery, searcher, targetTable).
        window(0, (config.maxSampleSize * percentUnlabelled).toInt)
      (hits, hits.size())
    }
    logger.debug(s"Read $unlabelledSize unlabelled hits " +
      s"in ${unlabelledReadTime.toMillis / 1000.0} seconds")

    val lastUnlabelledDoc = unlabelledHits.get(unlabelledSize - 1).doc
    logger.debug(s"Reading labelled hits, starting from doc $lastUnlabelledDoc")
    val ((labelledHits, labelledSize), labelledReadTime) = Timing.time {
      val hits = hitGatherer.getLabelledSample(tokenizedQuery, searcher, targetTable,
        lastUnlabelledDoc).window(0, (config.maxSampleSize * (1 - percentUnlabelled)).toInt)
      (hits, hits.size)
    }
    logger.debug(s"Read $labelledSize labelled hits in" +
      s" ${labelledReadTime.toMillis / 1000.0} seconds")

    val maxRemoves = if (narrow) {
      Int.MaxValue
    } else {
      querySuggestionConf.getConfig("broaden").getInt("maxRemoves")
    }

    logger.debug("Analyzing hits")

    val (unprunnedHitAnalysis, analysisTime) = Timing.time {
      val generator = if (narrow) {
        val selectConf = querySuggestionConf.getConfig("narrow")
        val clusterSizes = if (config.allowClusters) {
          selectConf.getIntList("clusterSizes").asScala.map(_.toInt)
        } else {
          Seq()
        }
        SpecifyingOpGenerator(
          selectConf.getBoolean("suggestPos"),
          selectConf.getBoolean("suggestWord"),
          clusterSizes
        )
      } else {
        val selectConf = querySuggestionConf.getConfig("broaden")
        val clusterSizes = if (config.allowClusters) {
          selectConf.getIntList("clusterSizes").asScala.map(_.toInt)
        } else {
          Seq()
        }
        GeneralizingOpGenerator(
          selectConf.getBoolean("suggestPos"),
          selectConf.getBoolean("suggestWord"),
          clusterSizes,
          selectConf.getBoolean("suggestAddWords"),
          tokenizedQuery.size,
          selectConf.getBoolean("generalizationPruning"),
          maxRemoves
        )
      }
      val (prefixCounts, suffixCounts) = if (narrow) {
        (
          querySuggestionConf.getConfig("narrow").getInt("prefixSize"),
          querySuggestionConf.getConfig("narrow").getInt("suffixSize")
        )
      } else {
        (0, 0)
      }
      buildHitAnalysis(Seq(labelledHits, unlabelledHits), tokenizedQuery,
        prefixCounts, suffixCounts, generator, targetTable)
    }
    logger.debug(s"Done Analyzing hits in ${analysisTime.toMillis / 1000.0} seconds")

    val beforePruning = unprunnedHitAnalysis.operatorHits.size
    val hitAnalysis =
      if ((unlabelledSize + labelledSize) >
        querySuggestionConf.getInt("pruneOperatorsIfMoreMatchesThan")) {
        val pruneLessThan = querySuggestionConf.getInt("pruneOperatorsIfLessThan")
        unprunnedHitAnalysis.copy(
          operatorHits = unprunnedHitAnalysis.operatorHits.filter {
            case (token, matches) => matches.size > pruneLessThan
          }
        )
      } else {
        unprunnedHitAnalysis
      }

    logger.debug(s"Pruned ${beforePruning - hitAnalysis.operatorHits.size} " +
      s"(${hitAnalysis.operatorHits.size} operators left)")
    val totalPositiveHits = hitAnalysis.examples.count(x => x.label == Positive)
    val totalNegativeHits = hitAnalysis.examples.count(x => x.label == Negative)
    logger.info(s"Found $totalPositiveHits positive " +
      s"and $totalNegativeHits negative with ${hitAnalysis.operatorHits.size} possible operators")

    val lastDoc = if (labelledSize > 0) {
      labelledHits.get(labelledSize - 1).doc
    } else {
      lastUnlabelledDoc
    }

    val opCombiner =
      if (config.allowDisjunctions) {
        (x: EvaluatedOp) => OpConjunctionOfDisjunctions.apply(x, maxRemoves)
      } else {
        (x: EvaluatedOp) => OpConjunction.apply(x, maxRemoves)
      }

    val unlabelledBiasCorrection = lastDoc / lastUnlabelledDoc.toDouble
    val evalFunction =
      if (narrow) {
        SumEvaluator(
          hitAnalysis.examples,
          config.pWeight, config.nWeight, config.uWeight * unlabelledBiasCorrection
        )
      } else {
        PartialSumEvaluator(
          hitAnalysis.examples,
          config.pWeight, config.nWeight, config.uWeight * unlabelledBiasCorrection, config.depth
        )
      }

    val operators = if (totalPositiveHits > 0) {
      logger.debug("Selecting query...")
      val (operators, searchTime) = Timing.time {
        QuerySuggester.selectOperator(
          hitAnalysis,
          evalFunction,
          opCombiner,
          config.beamSize,
          config.depth,
          Some(tokenizedQuery)
        )
      }
      logger.debug(s"Done selecting in ${searchTime.toMillis / 1000.0}")
      operators
    } else {
      logger.info("Not enough positive hits found")
      Seq()
    }

    val scoredOps = operators.flatMap {
      case (op, score) =>
        val query = op.applyOps(tokenizedQuery).getQuery
        val (p, n, u) = QueryEvaluator.countOccurrences(op.numEdits, hitAnalysis.examples)
        if (score > Double.NegativeInfinity) {
          Some(ScoredQuery(query, score, p, n, u * unlabelledBiasCorrection))
        } else {
          None
        }
    }.take(querySuggestionConf.getInt("numToReturn"))

    val original = {
      val edits = IntMap(Range(0, hitAnalysis.examples.size).map((_, 0)): _*)
      val score = evalFunction.evaluate(NullOp(edits), config.depth)
      val (p, n, u) = QueryEvaluator.countOccurrences(
        IntMap(Range(0, hitAnalysis.examples.size).map((_, 0)): _*),
        hitAnalysis.examples
      )
      ScoredQuery(startingQuery, score, p, n, u * unlabelledBiasCorrection)
    }
    logger.info(s"Done suggesting query for " +
      "${QueryLanguage.getQueryString(queryWithNamedCaptures)}")
    Suggestions(original, scoredOps, lastDoc)
  }
}
