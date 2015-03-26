package org.allenai.dictionary.ml

import nl.inl.blacklab.search._
import org.allenai.common.{ Logging, Timing }
import org.allenai.dictionary._
import org.allenai.dictionary.ml.compoundops._
import org.allenai.dictionary.ml.primitveops._
import org.allenai.dictionary.ml.subsample._
import scala.collection.JavaConverters._
import scala.collection.immutable.IntMap

case class ScoredOps(ops: CompoundQueryOp, score: Double, msg: String)
case class ScoredQuery(query: QExpr, score: Double, msg: String)

object Label extends Enumeration {
  type Label = Value
  val Positive, Negative, Unlabelled = Value
}
import Label._

/** A particular sentence from the corpus we will evaluate queries on.
  *
  * @param label Label of the sentence
  * @param requiredEdits The 'edit distance' from our current query to a query that would match this
  *                    example. Hence the number of operators we will need to apply to the
  *                    starting query if we want it to match this sentence.
  * @param str The sentence, stored for logging / debugging purposes
  */
case class Example(label: Label, requiredEdits: Int, str: String)

object QuerySuggester extends Logging {

  /** Percent of examples we will use when evaluating queries that should be unlabelled */
  val numUnlabelled = 0.3

  /** Some cache analysis for a subsample of hits, in particular records the labels for each
    * sentence in the subsample and which sentences each primitive operation applies to.
    *
    * @param operatorHits Maps primitive operations to map for (sentence index with in examples ->
    *                   number of required 'edit' that operator completes for that sentence
    *                   (can be 0))
    * @param examples List of examples, one for each sentence
    */
  case class HitAnalysis(
                            operatorHits: Map[TokenQueryOp, IntMap[Int]],
                            examples: IndexedSeq[Example]
                            ) {

    def size: Int = examples.size

    def ++(other: HitAnalysis): HitAnalysis = {
      val s = size
      def appendMaps(m1: IntMap[Int], m2: IntMap[Int]): IntMap[Int] = {
        m1 ++ (m2 map { case (a, b) => (a + s, b) })
      }

      val allOperator = operatorHits.keys.toSet ++ other.operatorHits.keys
      HitAnalysis(
        allOperator.map(x => {
          val otherHits = other.operatorHits.getOrElse(x, IntMap[Int]())
          val myHits = operatorHits.getOrElse(x, IntMap[Int]())
          (x, appendMaps(myHits, otherHits))
        }).toMap,
        examples ++ other.examples
      )
    }
  }

  /** Builds a HitAnalysis object from a sequence a series of Hits
    *
    * @param hits Hits to build the HitAnalysis object from, it will contain one Example
    *            for each hit in Hits.
    * @param generators generators to generate primitive operations from
    * @param positiveTerms Positive examples
    * @param negativeTerms Negative examples
    * @return HitAnalysis object for the given Hits object.
    */
  def buildHitAnalysis(hits: Hits,
                       generators: Seq[TokenQueryOpGenerator],
                       positiveTerms: Set[TableRow],
                       negativeTerms: Set[TableRow]): HitAnalysis = {
    val positiveStrings = positiveTerms.map(_.values.head.qwords.
        map(_.value).mkString(" "))
    val negativeStrings = negativeTerms.map(_.values.head.qwords.
        map(_.value).mkString(" "))
    val operatorMap = scala.collection.mutable.Map[TokenQueryOp, List[(Int, Int)]]()
    var examples = List[Example]()
    hits.setContextSize(generators.map(_.requiredContextSize).max)
    hits.setContextField((generators.flatMap(_.requiredProperties).toSet + "word").toSeq.asJava)
    hits.asScala.zipWithIndex.foreach {
      case (hit, index) =>
        val kwic = hits.getKwic(hit)
        val captureGroups = hits.getCapturedGroups(hit)
        val captureSpanOption = captureGroups.headOption
        if (captureSpanOption.isEmpty) {
          // Should not occur, but (due to a bug in BlackLab?) sometimes it happens anyway
          logger.warn("Got an empty capture group hit, skipping")
        } else {
          val captureSpan = captureSpanOption.get
          val shift = hit.start - kwic.getHitStart
          val capturedString = kwic.getTokens("word").subList(
            captureSpan.start - shift,
            captureSpan.end - shift
          ).asScala.mkString(" ")
          generators.foreach(
            _.generateOperations(hit, hits).foreach(
              op => {
                val currentList = operatorMap.getOrElse(op.op, List[(Int, Int)]())
                operatorMap.put(op.op, (index, if (op.required) 1 else 0) :: currentList)
              }
            )
          )
          val label = if (positiveStrings contains capturedString.toLowerCase) {
            Positive
          } else if (negativeStrings contains capturedString.toLowerCase) {
            Negative
          } else {
            Unlabelled
          }
          val str = kwic.getMatch("word").asScala.mkString(" ")
          val requiredEdits = captureGroups.drop(1).count(_ != null)
          examples = Example(label, requiredEdits, str) :: examples
        }
    }
    HitAnalysis(
      operatorMap.map { case (k, v) => (k, IntMap(v: _*)) }.toMap,
      examples.reverse.toIndexedSeq
    )
  }

  /** Uses a beam search to select the best CompoundQueryOp
    *
    * @param hitAnalysis data to use when evaluating queries
    * @param evaluationFunction function to evaluate CompoundQueryOps with
    * @param opBuilder Builder function for CompoundQueryOps
    * @param beamSize Size of the beam to use in the search
    * @param depth Depth to run the search to, corresponds the to maximum size
    *            of a CompoundQueryOp that can be proposed
    * @param maxReturn Maximum number of CompoundQueryOpss to return
    * @return Sequence of CompoundQueryOps, together with their score and a string
    *       message about some statistics about that op.
    */
  def selectOperator(hitAnalysis: HitAnalysis,
                     evaluationFunction: QueryEvaluator,
                     opBuilder: EvaluatedOp => CompoundQueryOp,
                     beamSize: Int,
                     depth: Int,
                     maxReturn: Int): Seq[ScoredOps] = {

    // Reverse ordering so the smallest scoring operators are at the head
    val priorityQueue = scala.collection.mutable.PriorityQueue()(
      Ordering.by[(CompoundQueryOp, Double), Double](_._2)
    ).reverse

    // Add length1 operators to the queue
    hitAnalysis.operatorHits.foreach {
      case (operator, matches) =>
        val op = opBuilder(EvaluatedOp(operator, matches))
        val score = evaluationFunction.evaluate(op, 1)
        if (priorityQueue.size < beamSize) {
          priorityQueue.enqueue((op, score))
        } else if (priorityQueue.head._2 < score) {
          priorityQueue.dequeue()
          priorityQueue.enqueue((op, score))
        }
    }

    def printQueue(depth: Int): Unit = {
      priorityQueue.foreach {
        case (op, score) => logger.debug(
          s"${op.toString}::(${evaluationFunction.evaluationMsgLong(op, depth)})"
        )
      }
    }

    val alreadyFound = scala.collection.mutable.Set[CompoundQueryOp]()

    // Start the beam search, note this search follows a breadth-first pattern where we keep a fixed
    // number of nodes at each depth rather then the more typical depth-first style of beam search.
    for (i <- 1 until depth) {
      logger.debug(s"********* Depth $i *********")
      printQueue(i + 1)
      logger.debug(s"********* Starting Depth ${i + 1} *********")

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
        // expanding them
        case (operator, score) => operator.ops.size == i
      }.to[Seq].foreach { // iterate over a copy so we do not re-expand nodes
        case (operator, score) =>
          hitAnalysis.operatorHits.
              // Filter our unusable operators
              filter { case (newOp, opMatches) => operator.canAdd(newOp) }.
              // Map the remaining operators to new compound rules
              foreach {
            case (newOp, opMatches) =>
              val newOperator = operator.add(EvaluatedOp(newOp, opMatches))
              if (!alreadyFound.contains(newOperator)) {
                val newScore = evaluationFunction.evaluate(newOperator, 1 + i)
                if (priorityQueue.size < beamSize) {
                  priorityQueue.enqueue((newOperator, newScore))
                } else if (newScore > priorityQueue.head._2) {
                  priorityQueue.dequeue()
                  priorityQueue.enqueue((newOperator, newScore))
                }
                alreadyFound.add(newOperator)
              }
          }
        }
      }

    logger.debug("******* DONE ************")
    printQueue(depth)
    priorityQueue.dequeueAll.reverse.take(maxReturn).map(x => ScoredOps(x._1, x._2,
      evaluationFunction.evaluationMsg(x._1, depth))).toSeq
  }

  /** Return a set of suggested queries given a starting query and a Table
    *
    * @param searcher Searcher to the corpus to use when suggesting new queries
    * @param startingQuery Starting query to build suggestion from
    * @param tables Tables to use when building the query
    * @param target Name of the table to optimize the suggested queries for
    * @param narrow Whether the suggestions should narrow or broaden the starting
    *             query
    * @param config Configuration details to use when suggesting the new queries
    * @return Suggested queries, along with their scores and a String msg details some
    *       statistics about each query.
    */
  def suggestQuery(searcher: Searcher,
                   startingQuery: QExpr,
                   tables: Map[String, Table],
                   target: String,
                   narrow: Boolean,
                   config: SuggestQueryConfig): Seq[ScoredQuery] = {

    val targetTable = tables.get(target) match {
      case Some(table) => table
      case None => throw new IllegalArgumentException("Target table not found")
    }

    val tokenizedQuery = TokenizedQuery.buildFromQuery(startingQuery)

    logger.debug(s"Making ${if (narrow) "narrowing" else "broadening"} " +
        s"suggestion for <${QueryLanguage.getQueryString(startingQuery)}> for $target")
    logger.debug(s"Config: $config")

    val positiveTerms = targetTable.positive.toSet
    val negativeTerms = targetTable.negative.toSet

    val tokenSeq = tokenizedQuery.getSeq

    def parseHits(hits: HitsWindow): HitAnalysis = {
      val generators =
        if (narrow) {
          Seq(
            PrefixOpGenerator(QLeafGenerator(Set("word", "pos")), Seq(1, 2, 3)),
            PrefixOpGenerator(QLeafGenerator(Set("word", "pos")), Seq(1, 2, 3)),
            ReplaceTokenGenerator.specifyTokens(
              tokenizedQuery.getSeq,
              Range(1, tokenSeq.size + 1),
              Seq("pos"), Seq(2, 4, 6)
            )
          )
        } else {
          hits.get(0) // Ensure hits has loaded the captureGroupNames
          val captureNames = hits.getCapturedGroupNames
          val targetCaptureNames = SpansFuzzySequence.getMissesCaptureGroupNames(tokenSeq.size)
          val targetIndices = targetCaptureNames.map(x => captureNames.indexOf(x))
          Seq(RequiredEditsGenerator(
            QLeafGenerator(Set("pos")),
            QLeafGenerator(Set()),
            targetIndices
          ))
        }
      QuerySuggester.buildHitAnalysis(hits, generators, positiveTerms, negativeTerms)
    }

    val hitGatherer = if (narrow) {
      MatchesSampler()
    } else {
      FuzzySequenceSampler(1, config.depth)
    }

    logger.debug(s"Retrieving labelled documents...")
    val (labelledHitAnalysis, labelledRetrieveTime) = Timing.time {
      val hits = hitGatherer.getLabelledSample(startingQuery, searcher, targetTable).
          window(0, config.maxSampleSize)
      val analysis = parseHits(hits.window(
        0,
        config.maxSampleSize - (config.maxSampleSize * numUnlabelled).toInt
      ))
      Some(analysis)
    }
    logger.debug(s"Document retrieval took ${labelledRetrieveTime.toMillis / 1000.0} seconds")

    logger.debug(s"Retrieving unlabelled documents...")
    val (unprunnedHitAnalysis, unlabelledRetrieveTime) = Timing.time {
      val hits = hitGatherer.getSample(startingQuery, searcher)
      val window = hits.window(0, (config.maxSampleSize * numUnlabelled).toInt)
      val analysis = parseHits(window)
      if (labelledHitAnalysis.isEmpty) analysis else labelledHitAnalysis.get ++ analysis
    }
    logger.debug(s"Retrieval took ${unlabelledRetrieveTime.toMillis / 1000.0} seconds")

    val beforePruning = unprunnedHitAnalysis.operatorHits.size
    val hitAnalysis = unprunnedHitAnalysis.copy(
      operatorHits = unprunnedHitAnalysis.operatorHits.filter {
        case (token, matches) => matches.size > 1
      }
    )
    logger.debug(s"Pruned ${beforePruning - hitAnalysis.operatorHits.size} operators")

    val totalPositiveHits = hitAnalysis.examples.count(x => x.label == Positive)
    val totalNegativeHits = hitAnalysis.examples.count(x => x.label == Negative)
    logger.debug(s"Done parsing ${hitAnalysis.size} hits")
    logger.debug(s"Found $totalPositiveHits positive " +
        s"and $totalNegativeHits negative with ${hitAnalysis.operatorHits.size} possible operators")

    if (totalPositiveHits == 0) {
      logger.debug("Not enough positive hits found")
      return Seq()
    }

    val opCombiner =
      if (config.allowDisjunctions) {
        (x: EvaluatedOp) => OpConjunctionOfDisjunctions.apply(x)
      } else {
        (x: EvaluatedOp) => OpConjunction.apply(x)
      }

    val evalFunction =
      if (narrow) {
        CoverageSum(
          hitAnalysis.examples,
          config.pWeight, config.nWeight, config.uWeight
        )
      } else {
        WeightedCoverageSum(
          hitAnalysis.examples,
          config.pWeight, config.nWeight, config.uWeight, config.depth
        )
      }

    logger.debug("Selecting query...")
    val (operators, searchTime) = Timing.time {
      QuerySuggester.selectOperator(
        hitAnalysis,
        evalFunction,
        opCombiner,
        config.beamSize, config.depth,
        10
      )
    }
    logger.debug(s"Done selecting in ${searchTime.toMillis / 1000.0}")

    val suggestedQueries = operators.map { scoredOp =>
      ScoredQuery(scoredOp.ops.applyOps(tokenizedQuery).getQuery, scoredOp.score, scoredOp.msg)
    }
    suggestedQueries.take(10)
  }
}
