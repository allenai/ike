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
  * words
  * @param doc the document number this Example came from
  * @param str String of hit, kept only for debugging purposes
  */
case class Example(label: Label, requiredEdits: Int,
  captureStrings: Seq[Seq[String]], doc: Int, str: String = "")

/** Example, but with an associated weight and phraseId
  *
  * @param label label of the Hit
  * @param phraseId id for the phrase this Hit captured, Examples that captured the same phrase
  *                 will have the same id
  * @param requiredEdits number of query-tokens we need to edit for the starting query to match
  * this hit (see the ml/README.md)
  * @param doc document this example came from
  * @param weight weight indicating how important it is to match this Example
  * @param str String of the hit, again a debugging tool
  */
case class WeightedExample(label: Label, phraseId: Int, requiredEdits: Int, doc: Int,
  weight: Double, str: String = "")

object QuerySuggester extends Logging {

  /** Returns an estimate of the number unlabelled phrases we expected to find is we searched
    * docToExtrapolateTo, given we gathered unlabelledExamples by searching up to lastDocSearched
    *
    * @param unlabelledExamples unlabelled Examples
    * @param lastDocSearched number of documents the unlabelled examples were gathered from
    * @param docToExtrapolateTo number of documents we would expect to search
    * @return expected number of phrases found
    */
  def getExpectedUnlabelledPhrases(
    unlabelledExamples: IndexedSeq[WeightedExample],
    lastDocSearched: Int,
    docToExtrapolateTo: Int
  ): Double = {
    // Ideal we could fit a logarithmic curve to the data, but for now we just use the heuristic of
    // fitting a linear curve to the last half of the data. In theory we should also be
    // doing calculations per the results returned by each searcher as well
    require(lastDocSearched < docToExtrapolateTo)
    if (unlabelledExamples.isEmpty) {
      0
    } else {
      var phrasesFound = Set[Int]()
      // Count the number of new phrases that were introduced in all documents after the
      // median document, we use the median doc only so our split contains hits from
      // non-overlapping documents
      val sortedDocs = unlabelledExamples.map(_.doc).sorted
      val medianDoc = sortedDocs(sortedDocs.size / 2)
      val numNewPhrasesFromMedianDoc = unlabelledExamples.groupBy(_.doc).toSeq.sortBy(_._1).map {
        case (doc, examplesInDoc) =>
          if (doc > medianDoc) {
            val oldSize = phrasesFound.size
            phrasesFound ++= examplesInDoc.map(_.phraseId)
            phrasesFound.size - oldSize
          } else {
            phrasesFound ++= examplesInDoc.map(_.phraseId)
            0
          }
      }.sum

      // Now assuming that new phrases continue to be introduced at the same rate, extrapolate
      // to get the number of phrases we expect
      val expectedNewPhrasesPerDoc = numNewPhrasesFromMedianDoc.toDouble /
        (lastDocSearched - medianDoc)
      val docsMissing = docToExtrapolateTo - lastDocSearched
      val expectedNewPhrases = phrasesFound.size + expectedNewPhrasesPerDoc * docsMissing
      // We expect the naive linear interpolation to produce predict more results, but if not
      // (maybe because our sample size was small/noisy) so we prefer the linear interpolation
      val linearExpectedNewPhrases = phrasesFound.size +
        phrasesFound.size.toDouble / lastDocSearched * docsMissing
      Math.min(linearExpectedNewPhrases, expectedNewPhrases)
    }
  }

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

    // Trys to add the given operator with given score to the queue while avoiding over-using the
    // same operator
    def addOp(cop: CompoundQueryOp, score: Double): Boolean = {
      val queueFull = priorityQueue.size >= beamSize
      val opAdded = if (!queueFull || priorityQueue.head._2 < score) {
        val repeatedOps = cop.ops.filter(opsUsed(_) >= maxOpOccurances)
        if (repeatedOps.nonEmpty) {
          // Adding would result in overusing an op, so we try to find an element in the queue
          // that has a lower score and that we can remove to allow us to add this op
          // Cases where two ops are over-used should maybe be treated specially (maybe we should
          // remove multiple elements?) but this is not currently implemeneted
          val candidatesForRemoval = priorityQueue.filter {
            case (op, __) => repeatedOps.forall(op.ops.contains)
          }
          if (candidatesForRemoval.nonEmpty) {
            val min = candidatesForRemoval.minBy(_._2)
            if (min._2 < score) {
              // Super expensive way to remove since we re-sort the entire queue, but there is no
              // remove API for queues as far as I can see. This is not in a performance limiting
              // loop anyway
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

      if (evaluationFunction.usesDepth) {
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
                val newOperator = operator.add(newOp, opMatches)
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

  case class SearcherExamples(unlabelled: Hits, labelled: Hits,
    docsSearchedForUnlabelled: Int, totalDocsSearched: Int)

  // Get examples for the given query from a given searcher
  private def getExamples(query: TokenizedQuery, searcher: Searcher, targetTable: Table,
    tables: Map[String, Table], sampler: Sampler,
    maxUnlabelled: Int, maxLabelled: Int): SearcherExamples = {
    val unlabelledHits =
      sampler.getSample(query, searcher, targetTable, tables).window(0, maxUnlabelled)
    val lastDoc = unlabelledHits.get(unlabelledHits.last()).doc
    val lastToken = unlabelledHits.get(unlabelledHits.last()).end
    val docsSearchedForUnlabelled = if (unlabelledHits.size() < maxUnlabelled) {
      // We must have scanned the entire index
      searcher.getIndexReader.numDocs()
    } else {
      // Assume docIds are in order and consecutive, this should be safe as long as
      // we do not delete documents.
      unlabelledHits.get(unlabelledHits.last()).doc
    }

    if (Thread.interrupted()) throw new InterruptedException()

    val labelledHits = sampler.getLabelledSample(query, searcher, targetTable,
      tables, lastDoc, lastToken + 1).window(0, maxLabelled)

    val totalDocsSearched = if (labelledHits.size() < maxLabelled) {
      searcher.getIndexReader.numDocs()
    } else {
      labelledHits.get(labelledHits.last()).doc
    }
    SearcherExamples(unlabelledHits, labelledHits, docsSearchedForUnlabelled, totalDocsSearched)
  }

  /** Return a set of suggested queries given a starting query and a Table
    *
    * @param searchers Searchers to use when suggesting new queries
    * @param startingQuery Starting query to build suggestion from, should have all QGeneralize
    *                      interpolated to QSimilarPhrases
    * @param tables Tables to use when building the query
    * @param similarPhrasesSearcher to use when building similar phrase suggestions
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
    similarPhrasesSearcher: SimilarPhrasesSearcher,
    target: String,
    narrow: Boolean,
    config: SuggestQueryConfig
  ): Suggestions = {
    val targetTable = tables.get(target) match {
      case Some(table) => table
      case None => throw new IllegalArgumentException("Target table not found")
    }
    val percentUnlabelled = querySuggestionConf.getDouble("percentUnlabelled")

    val queryWithNamedCaptures =
      QueryLanguage.interpolateSimilarPhrases(startingQuery, similarPhrasesSearcher).get

    logger.info(s"Making ${if (narrow) "narrowing" else "broadening"} " +
      s"suggestion for <${QueryLanguage.getQueryString(queryWithNamedCaptures)}> for $target")
    logger.info(s"Configuration: $config")

    val tokenizedQuery =
      if (narrow) {
        TokenizedQuery.buildFromQuery(queryWithNamedCaptures, targetTable.cols)
      } else {
        val tq = TokenizedQuery.buildWithGeneralizations(queryWithNamedCaptures, searchers,
          targetTable.cols, similarPhrasesSearcher, 100)
        tq.getSeq.zip(tq.generalizations.get).foreach {
          case (q, g) => logger.debug(s"Generalize $q => $g")
        }
        tq
      }

    val hitGatherer = if (narrow) {
      MatchesSampler()
    } else {
      GeneralizedQuerySampler(
        Math.min(tokenizedQuery.size, config.depth),
        querySuggestionConf.getConfig("broaden").getInt("wordPOSSampleSize")
      )
    }

    val maxUnlabelledPerSearcher = (config.maxSampleSize * percentUnlabelled).toInt / searchers.size
    val maxLabelledPerSearcher = config.maxSampleSize - maxUnlabelledPerSearcher
    logger.debug("Fetching examples...")
    val (allExamples, allExampleTime) = Timing.time {
      val allExamples = searchers.par.map { searcher =>
        getExamples(tokenizedQuery, searcher, targetTable, tables,
          hitGatherer, maxUnlabelledPerSearcher, maxLabelledPerSearcher)
      }.seq
      allExamples
    }
    val numDocsSearched = allExamples.map(_.totalDocsSearched).sum
    val numDocsSearchedForUnlabelled = allExamples.map(_.docsSearchedForUnlabelled).sum
    val allHits = allExamples.flatMap(examples => Seq(examples.unlabelled, examples.labelled))

    logger.debug(s"Fetching examples took ${allExampleTime.toMillis / 1000.0} seconds, " +
      s"searched $numDocsSearched documents for ${allHits.map(_.size()).sum} examples")

    logger.debug("Analyzing hits")

    val (unprunnedHitAnalysis, analysisTime) = Timing.time {
      val generator = if (narrow) {
        val selectConf = querySuggestionConf.getConfig("narrow")
        SpecifyingOpGenerator(
          selectConf.getBoolean("suggestPos"),
          selectConf.getBoolean("suggestWord"),
          selectConf.getBoolean("suggestSetRepeatedOp"),
          selectConf.getInt("minSimilarityDifference")
        )
      } else {
        val selectConf = querySuggestionConf.getConfig("broaden")
        GeneralizingOpGenerator(
          selectConf.getBoolean("suggestPos"),
          selectConf.getBoolean("suggestWord"),
          selectConf.getInt("minSimilarityDifference")
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

      buildHitAnalysis(
        allHits,
        tokenizedQuery, prefixCounts, suffixCounts, generator, targetTable
      )
    }
    logger.debug(s"Done Analyzing hits in ${analysisTime.toMillis / 1000.0} seconds")

    // Prune operators that effect too few sentences
    val beforePruning = unprunnedHitAnalysis.operatorHits.size
    val hitAnalysis =
      if (numDocsSearched >
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
    logger.debug(s"Pruned ${beforePruning - hitAnalysis.operatorHits.size} operators due to size" +
      s"(${hitAnalysis.operatorHits.size} operators left)")

    val totalPositiveHits = hitAnalysis.examples.count(x => x.label == Positive)
    val totalNegativeHits = hitAnalysis.examples.count(x => x.label == Negative)
    logger.info(s"Found $totalPositiveHits positive " +
      s"and $totalNegativeHits negative with ${hitAnalysis.operatorHits.size} possible operators")

    val restrictDisjunctionTo = tokenizedQuery.getAnnotatedSeq.flatMap { qsd =>
      qsd.qexpr.get match {
        case QDisj(_) => Some(qsd.slot)
        case qr: QRepeating if qr.qexpr.isInstanceOf[QDisj] => Some(qsd.slot)
        case _ => None
      }
    }.toSet

    val opCombiner = (x: EvaluatedOp) => OpConjunctionOfDisjunctions(x, Some(restrictDisjunctionTo))

    // Since we might have biased our sample toward labelled documents, extrapolate to guess at the
    // number of unlabelled documents we would have seen if the whole sample was not biased
    val unlabelledPhrases = hitAnalysis.examples.filter(_.label == Unlabelled).
      map(_.phraseId).toSet.size
    val expectedUnlabelledPhrases = if (unlabelledPhrases == 0 ||
      numDocsSearched == numDocsSearchedForUnlabelled) {
      unlabelledPhrases
    } else {
      getExpectedUnlabelledPhrases(
        hitAnalysis.examples.filter(_.label == Unlabelled),
        numDocsSearchedForUnlabelled, numDocsSearched
      )
    }
    val unlabelledBiasCorrection =
      Math.min(
        expectedUnlabelledPhrases / unlabelledPhrases,
        querySuggestionConf.getDouble("maxUnlabelledBiasCorrection")
      )
    logger.debug(s"Unlabelled bias correction: $unlabelledBiasCorrection")
    val evalFunction =
      if (narrow) {
        SumEvaluator(
          hitAnalysis.examples,
          config.pWeight, config.nWeight, config.uWeight * unlabelledBiasCorrection, true
        )
      } else {
        PartialSumEvaluator(
          hitAnalysis.examples,
          config.pWeight, config.nWeight, config.uWeight * unlabelledBiasCorrection, config
          .depth, false
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
      val edits = IntMap(hitAnalysis.examples.indices.map((_, 0)): _*)
      val score = evalFunction.evaluate(NullOp(edits), config.depth)
      val (p, n, u) = QueryEvaluator.countOccurrences(
        edits,
        hitAnalysis.examples
      )
      ScoredQuery(startingQuery, score, p, n, u * unlabelledBiasCorrection)
    }

    // Remove queries the appear to be completely worthless, don't return
    val operatorsPrunedByScore = operators.filter(_._2 > Double.NegativeInfinity)

    val maxToSuggest = querySuggestionConf.getInt("numToSuggest")

    // Remove queries that fail our diversity check
    val operatorsPruneByRepetition = if (narrow) {
      var opsUsedCounts = operators.flatMap(_._1.ops).groupBy(identity).mapValues(_.size)
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
        val query = op.applyOps(tokenizedQuery).getOriginalQuery
        val (p, n, u) = QueryEvaluator.countOccurrences(op.numEdits, hitAnalysis.examples)
        ScoredQuery(query, score, p, n, u * unlabelledBiasCorrection)
    }
    logger.info(s"Done suggesting query for " +
      s"${QueryLanguage.getQueryString(queryWithNamedCaptures)}")
    Suggestions(original, scoredOps, numDocsSearched)
  }
}
