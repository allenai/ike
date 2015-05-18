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
  * this hit (see the ml/README.md)
  * @param captureStrings the string we captured, as a Sequence of capture groups of sequences of
  *   words
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
    maxOpOccurances: Int,
    query: Option[TokenizedQuery] = None
  ): Seq[(CompoundQueryOp, Double)] = {

    // Counts the number of time each TokenQueryOp occurs in our queue, used to promote diversity
    // within the queue by trying to avoid overusing the same op
    val opsUsed = scala.collection.mutable.Map[TokenQueryOp, Int]().withDefaultValue(0)

    // Best ops so far, reverse ordering so the smallest scoring operators are at the head
    val priorityQueue = scala.collection.mutable.PriorityQueue()(
      Ordering.by[(CompoundQueryOp, Double), Double](_._2).reverse
    )
    priorityQueue.sizeHint(depth)

    // Which set of Ops we have already found, used so we do not arrive at the same set of ops
    // while following different paths
    val alreadyFound = scala.collection.mutable.Set[Set[TokenQueryOp]]()

    // Remove the counts of the operators in op from opsUsed
    def removeCounts(op: CompoundQueryOp): Unit = {
      op.ops.foreach { tokenOp =>
        val current = opsUsed(tokenOp)
        if (current == 1) {
          opsUsed.remove(tokenOp)
        } else {
          opsUsed.update(tokenOp, current - 1)
        }
      }
    }

    // Trys to add the given operator with given score to the queue
    def addOp(cop: CompoundQueryOp, score: Double): Boolean = {
      val queueFull = priorityQueue.size >= beamSize
      val opAdded = if (!queueFull || priorityQueue.head._2 < score) {
        val repeatedOps = cop.ops.filter(opsUsed(_) >= maxOpOccurances)
        if (repeatedOps.nonEmpty) {
          // Adding would result in overusing an op, so we try to find an element in the queue
          // that is lowering scoring and that we can remove to allow us to add this op
          val candidatesForRemoval = priorityQueue.filter {
            case (op, __) => repeatedOps.forall(op.ops.contains(_))
          }
          if (candidatesForRemoval.nonEmpty) {
            val min = candidatesForRemoval.minBy(_._2)
            if (min._2 < score) {
              // Overly expensive way to remove since we re-sort the entire queue, but there is no
              // remove API for queues as far as I can see
              removeCounts(min._1)
              val newElements = priorityQueue.filter(_ != min).toSeq
              priorityQueue.clear()
              newElements.foreach(priorityQueue.enqueue(_))
              cop.ops.foreach(tokenOp => opsUsed.update(tokenOp, opsUsed(tokenOp) + 1))
              priorityQueue.enqueue((cop, score))
              true
            } else {
              false
            }
          } else {
            false
          }
        } else if (queueFull) {
          val removed = priorityQueue.dequeue()
          removeCounts(removed._1)
          cop.ops.foreach(tokenOp => opsUsed.update(tokenOp, opsUsed(tokenOp) + 1))
          priorityQueue.enqueue((cop, score))
          true
        } else {
          cop.ops.foreach(tokenOp => opsUsed.update(tokenOp, opsUsed(tokenOp) + 1))
          priorityQueue.enqueue((cop, score))
          true
        }
      } else {
        false
      }
      // assert(priorityQueue.map(_._1.ops).flatten.groupBy(x => x).mapValues(_.size) == opsUsed)
      // assert(opsUsed.values.forall(_ <= maxOpOccurances), opsUsed)
      opAdded
    }

    // Add length1 operators to the queue
    hitAnalysis.operatorHits.foreach {
      case (operator, matches) =>
        val op = opBuilder(EvaluatedOp(operator, matches))
        if (op.isDefined) {
          val score = evaluationFunction.evaluate(op.get, 1)
          addOp(op.get, score)
        }
    }

    def logQueue(depth: Int): Unit = {
      priorityQueue.clone().dequeueAll.foreach {
        case (op, score) =>
          val opStr = if (query.isEmpty) op.toString() else op.toString(query.get)
          logger.debug(s"$opStr\n${evaluationFunction.evaluationMsg(op, depth)}")
      }
    }

    // Start the beam search, note this search follows a breadth-first pattern where we keep a fixed
    // number of nodes at each depth rather then the more typical depth-first style of beam search.
    for (i <- 1 until depth) {
      logger.debug(s"********* Depth $i *********")
      logQueue(i + 1)

      if (evaluationFunction.usesDepth()) {
        // Now the depth has changes, rescore the old queries so their scores are up-to-date
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
      }.to[Seq].foreach { // iterate over a copy so we do not expand we added in this loop
        case (operator, score) =>
          if (Thread.interrupted()) {
            throw new InterruptedException()
          }
          hitAnalysis.operatorHits.
            // Filter our unusable operators
            filter { case (newOp, opMatches) => operator.canAdd(newOp) }.
            // Map the remaining operators to new compound rules
            foreach {
              case (newOp, opMatches) =>
                val newOperator = operator.add(EvaluatedOp(newOp, opMatches))
                if (!alreadyFound.contains(newOperator.ops)) {
                  val newScore = evaluationFunction.evaluate(newOperator, 1 + i)
                  if (addOp(newOperator, newScore)) alreadyFound.add(newOperator.ops)
                }
            }
      }
    }

    logger.debug("******* DONE ************")
    logQueue(depth)
    priorityQueue.dequeueAll.reverse.toSeq
  }

  /** Return a set of suggested queries given a starting query and a Table
    *
    * @param searchers Searchers to use when suggesting new queries
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
    searchers: Seq[Searcher],
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

    val queryWithNamedCaptures = QueryLanguage.nameCaptureGroups(
      startingQuery,
      targetTable.cols
    )
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
    val numUnlabelledPerSearcher = (config.maxSampleSize * percentUnlabelled).toInt / searchers.size
    val numDocsPerSearcher = searchers.map(_.getIndexReader.numDocs())
    val ((unlabelledHits, unlabelledSize), unlabelledReadTime) = Timing.time {
      val hits = searchers.map(searcher =>
        hitGatherer.getSample(tokenizedQuery, searcher, targetTable, tables).
          window(0, numUnlabelledPerSearcher))
      (hits, hits.map(_.size()).sum)
    }
    logger.debug(s"Read $unlabelledSize unlabelled hits " +
      s"in ${unlabelledReadTime.toMillis / 1000.0} seconds")
    if (unlabelledSize == 0) {
      logger.info("No unlabelled documents found")
      return Suggestions(ScoredQuery(startingQuery, 0, 0, 0, 0), Seq(), 0)
    }

    // Number of documents we searched for unlabelled hits from
    val numUnlabelledDocs = unlabelledHits.zip(numDocsPerSearcher).map {
      case (hits, totalDocs) =>
        if (hits.size() < numUnlabelledPerSearcher) {
          totalDocs
        } else {
          hits.numberOfDocs()
        }
    }.sum
    val unlabelledEndPoints = unlabelledHits.map { hits =>
      if (hits.last() == -1) {
        (0, 0)
      } else {
        (hits.get(hits.last()).doc, hits.get(hits.last()).start)
      }
    }
    logger.debug(s"Reading labelled hits")
    val numLabelledPerSearcher = config.maxSampleSize - numUnlabelledPerSearcher
    val ((labelledHits, labelledSize), labelledReadTime) = Timing.time {
      val hits = searchers.zip(unlabelledEndPoints).map {
        case (searcher, (lastDoc, lastToken)) =>
          hitGatherer.getLabelledSample(tokenizedQuery, searcher, targetTable,
            tables, lastDoc, lastToken + 1).window(0, numLabelledPerSearcher)
      }
      (hits, hits.map(_.size).sum)
    }
    logger.debug(s"Read $labelledSize labelled hits in" +
      s" ${labelledReadTime.toMillis / 1000.0} seconds")

    // Number of documents we searched for labelled hits from
    val numDocs = (labelledHits, unlabelledEndPoints, numDocsPerSearcher).zipped.map {
      case (hits, (lastDoc, lastToken), totalDocs) =>
        if (hits.size < numUnlabelledPerSearcher) {
          totalDocs - lastDoc
        } else {
          hits.numberOfDocs()
        }
    }.sum

    val maxRemoves = if (narrow) {
      Int.MaxValue
    } else {
      querySuggestionConf.getConfig("broaden").getInt("maxRemoves")
    }

    logger.debug("Analyzing hits")

    val (unprunnedHitAnalysis, analysisTime) = Timing.time {
      val generator = if (narrow) {
        val selectConf = querySuggestionConf.getConfig("narrow")
        SpecifyingOpGenerator(
          selectConf.getBoolean("suggestPos"),
          selectConf.getBoolean("suggestWord"),
          selectConf.getBoolean("suggestSetRepeatedOp")
        )
      } else {
        val selectConf = querySuggestionConf.getConfig("broaden")
        GeneralizingOpGenerator(
          selectConf.getBoolean("suggestPos"),
          selectConf.getBoolean("suggestWord"),
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
      buildHitAnalysis(labelledHits ++ unlabelledHits, tokenizedQuery,
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

    val opCombiner =
      if (config.allowDisjunctions) {
        (x: EvaluatedOp) => OpConjunctionOfDisjunctions.apply(x, maxRemoves)
      } else {
        (x: EvaluatedOp) => OpConjunction.apply(x, maxRemoves)
      }

    val unlabelledBiasCorrection =
      Math.min(
        Math.max(if (numUnlabelledDocs > 0) {
          numUnlabelledDocs / numDocs.toDouble
        } else {
          numDocs.toDouble
        }, 1), querySuggestionConf.getDouble("maxUnlabelledBiasCorrection")
      )
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
      val maxOpReuse =
        if (narrow) {
          math.max(
            querySuggestionConf.getInt("maxOpReusePercentOfBeamSize") * config.beamSize,
            querySuggestionConf.getInt("minMaxOpReuse")
          )
        } else {
          config.beamSize
        }
      val (operators, searchTime) = Timing.time {
        QuerySuggester.selectOperator(
          hitAnalysis,
          evalFunction,
          opCombiner,
          config.beamSize,
          config.depth,
          maxOpReuse,
          Some(tokenizedQuery)
        )
      }
      logger.debug(s"Done selecting in ${searchTime.toMillis / 1000.0}")
      operators
    } else {
      logger.info("Not enough positive hits found")
      Seq()
    }

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
      s"${QueryLanguage.getQueryString(queryWithNamedCaptures)}")

    // Remove queries the appear to be completely worthless, don't return
    val operatorsPrunedByScore = operators.filter(_._2 > Double.NegativeInfinity)

    val maxToSuggest = querySuggestionConf.getInt("numToSuggest")

    // Remove queries that fail our diversity check
    val operatorsPruneByRepetition = if (narrow) {
      var opsUsedCounts = operators.map(_._1.ops).flatten.groupBy(identity).mapValues(_.size)
      val maxReturnOpReuse = querySuggestionConf.getInt("maxOpReuseReturn")
      val maxToRemove = operatorsPrunedByScore.size - maxToSuggest
      // Remove the lowest scoring ops that fail our diversity check
      var removed = 0
      operatorsPrunedByScore.reverse.filter {
        case (op, _) => if (!op.ops.forall(opsUsedCounts(_) < maxReturnOpReuse)) {
          removed += 1
          op.ops.foreach(tokenOp =>
            opsUsedCounts = opsUsedCounts.updated(tokenOp, opsUsedCounts(tokenOp) - 1))
          removed > maxToRemove
        } else {
          true
        }
      }.reverse
    } else {
      operatorsPrunedByScore
    }

    val scoredOps = operatorsPruneByRepetition.take(maxToSuggest).map {
      case (op, score) =>
        val query = op.applyOps(tokenizedQuery).getQuery
        val (p, n, u) = QueryEvaluator.countOccurrences(op.numEdits, hitAnalysis.examples)
        ScoredQuery(query, score, p, n, u * unlabelledBiasCorrection)
    }
    logger.info(s"Done suggesting query for " +
      "${QueryLanguage.getQueryString(queryWithNamedCaptures)}")
    Suggestions(original, scoredOps, numDocs)
  }
}
