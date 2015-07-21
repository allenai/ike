package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary._
import org.allenai.dictionary.ml.{ QueryMatches, WeightedExample }

import scala.collection.immutable.IntMap

object SpecifyingOpGenerator {
  def getRepeatedOpMatch(
    matches: QueryMatches,
    leafGenerator: QLeafGenerator
  ): Map[SetRepeatedToken, IntMap[Int]] = {
    val operatorMap = scala.collection.mutable.Map[SetRepeatedToken, List[(Int, Int)]]()
    matches.matches.view.zipWithIndex.foreach {
      case (queryMatch, matchIndex) =>
        val tokens = queryMatch.tokens
        tokens.view.zipWithIndex.foreach {
          case (token, tokenIndex) =>
            leafGenerator.generateLeaves(token).foreach { qLeaf =>
              val op = SetRepeatedToken(matches.queryToken.slot, tokenIndex + 1, qLeaf)
              val currentList = operatorMap.getOrElse(op, List[(Int, Int)]())
              operatorMap.put(op, (matchIndex, if (queryMatch.didMatch) 0 else 1) :: currentList)
            }
        }
    }
    operatorMap.map { case (k, v) => k -> IntMap(v: _*) }.toMap
  }
}

/** Builds QueryOps that makes a query more specific (match strictly less sentences)
  *
  * @param suggestPos whether to build operators that add QPos
  * @param suggestWord whether to build operators that add QWord
  * @param setRepeatedOp whether to suggested SetRepeatedToken ops
  * @param minSimilarityDifference when building possible QSimilarityOps, only suggest ops where
  *                                that have pos values separated by at least this much
  */
case class SpecifyingOpGenerator(
    suggestPos: Boolean,
    suggestWord: Boolean,
    setRepeatedOp: Boolean = false,
    minSimilarityDifference: Int = 0
) extends OpGenerator {

  // Helper method the build a QLeafGenerator the gathers ops that would
  // specify the given input token
  private def getLeafGenerator(
    qexpr: QExpr,
    isCapture: Boolean
  ): QLeafGenerator = qexpr match {
    case QPos(_) => QLeafGenerator(pos = false, suggestWord && !isCapture)
    case QWildcard() => QLeafGenerator(suggestPos, suggestWord && !isCapture)
    case _ => QLeafGenerator(pos = false, word = false)
  }

  /** Generates ops for a QDisj, the QDisj can be inside a QRepetition or not  */
  def generateForDisj(qd: QDisj, matches: QueryMatches): Map[QueryOp, IntMap[Int]] = {
    // Note this assumes the disj is exclusive, e.i if one element of the
    // disjunction matches not other elements will
    // TODO we should try to check this and return nothing if we are not sure
    val queryTokenIndex = matches.queryToken.slot.token
    val posTagsInDisj = qd.qexprs.flatMap {
      case QPos(pos) => Some(pos)
      case _ => None
    }.toSet
    val wordsInDisj = qd.qexprs.flatMap {
      case QWord(word) => Some(word)
      case _ => None
    }.toSet
    if (posTagsInDisj.size + wordsInDisj.size == 0) {
      Map()
    } else {
      val removeMap = scala.collection.mutable.Map[QueryOp, List[(Int, Int)]]()
      matches.matches.view.zipWithIndex.foreach {
        case (queryMatch, index) =>
          val tokens = queryMatch.tokens
          val missingPos = (posTagsInDisj -- tokens.map(_.pos)).
            map(p => RemoveFromDisj(queryTokenIndex, QPos(p)))
          val missingWords = (wordsInDisj -- tokens.map(_.word)).
            map(w => RemoveFromDisj(queryTokenIndex, QWord(w)))
          val hit = (index, if (queryMatch.didMatch) 0 else 1)
          (missingWords ++ missingPos).foreach { op =>
            removeMap.update(op, hit :: removeMap.getOrElse(op, List()))
          }
      }
      removeMap.map { case (op, edits) => (op, IntMap(edits: _*)) }.toMap
    }
  }

  /** Generates ops for a QSimilarPhrases, if the QSimilarPhrases is inside a QRepetition repeats
    * should be set the min and max number of repetitions.
    */
  def generateForQSimilarPhrases(
    qsim: QSimilarPhrases,
    matches: QueryMatches,
    examples: IndexedSeq[WeightedExample],
    repeats: Option[(Int, Int)]
  ): Map[QueryOp, IntMap[Int]] = {
    val phraseMap = new SimilarPhraseMatchTracker(qsim)
    val slot = matches.queryToken.slot
    if (repeats.isEmpty) {
      matches.matches.view.zipWithIndex.foreach {
        case (queryMatch, index) =>
          val phrase = queryMatch.tokens.map(_.word)
          phraseMap.addPhrase(phrase, queryMatch.didMatch, index)
      }
    } else {
      val (min, max) = repeats.get
      matches.matches.view.zipWithIndex.foreach {
        case (queryMatch, index) =>
          val phrase = queryMatch.tokens.map(_.word).toIndexedSeq
          phraseMap.addPhrases(phrase, queryMatch.didMatch, index, min, max)
      }
    }
    val thresholds2Edit = phraseMap.generateOps(0, qsim.pos, minSimilarityDifference, examples)
    thresholds2Edit.map {
      case (threshold, edits) =>
        (SetToken(slot, qsim.copy(pos = threshold)), edits)
    }.toMap
  }

  def generateForNone(matches: QueryMatches): Map[QueryOp, IntMap[Int]] = {
    val leafMap = OpGenerator.buildLeafMap(QLeafGenerator(suggestPos, suggestWord), matches.matches)
    leafMap.map { case (k, v) => SetToken(matches.queryToken.slot, k) -> v }
  }

  def generateForQLeaf(
    qleafOpt: QLeaf,
    matches: QueryMatches,
    repeating: Boolean
  ): Map[QueryOp, IntMap[Int]] = {
    val leavesToUse = getLeafGenerator(qleafOpt, matches.queryToken.isCapture)
    val leafMap = OpGenerator.buildLeafMap(leavesToUse, matches.matches)
    leafMap.map { case (k, v) => SetToken(matches.queryToken.slot, k) -> v }
  }

  def generateForQRepeating(
    repeatingOp: QRepeating,
    matches: QueryMatches,
    examples: IndexedSeq[WeightedExample]
  ): Map[QueryOp, IntMap[Int]] = {
    val slot = matches.queryToken.slot
    val queryTokenIndex = matches.queryToken.slot.token
    val isCapture = matches.queryToken.isCapture

    // Get ops that involve removing the token
    val (childMin, childMax) = QueryLanguage.getQueryLength(repeatingOp.qexpr)
    val zeroMatchesEditMap = if (repeatingOp.min == 0) {
      val zeroMatches = matches.matches.view.zipWithIndex.filter {
        case (queryMatch, index) => queryMatch.tokens.isEmpty
      }.map {
        case (queryMatch, index) => (index, if (queryMatch.didMatch) 1 else 0)
      }
      IntMap(zeroMatches: _*)
    } else {
      IntMap()
    }
    val removeOps =
      if (zeroMatchesEditMap.nonEmpty) {
        Seq((
          RemoveToken(queryTokenIndex),
          zeroMatchesEditMap
        ))
      } else {
        Seq()
      }

    // Get ops the involve changing the child token
    val leafGenerator = getLeafGenerator(repeatingOp.qexpr, isCapture)
    val setChildOps = repeatingOp.qexpr match {
      case qsp: QSimilarPhrases =>
        generateForQSimilarPhrases(
          qsp, matches, examples, Some((repeatingOp.min, repeatingOp.max))
        )
      case qd: QDisj => generateForDisj(qd, matches)
      case ql: QLeaf =>
        val leafMap = OpGenerator.buildLeafMap(leafGenerator, matches.matches)
        leafMap.map {
          case (qLeaf, editMap) =>
            // If no tokens were matched changing the token won't effect that hit
            (SetToken(slot, qLeaf), editMap ++ zeroMatchesEditMap)
        }
      case _ => Map()
    }

    if (childMin != childMax) {
      // In this case it is harder to reason about the # of repetitions the query needs to match
      // each token sequence, so currently we stop here
      setChildOps ++ removeOps
    } else {
      // Gets op that involves changing the number of repetitions and tokens within a repetition
      case class Repetitions(index: Int, repeats: Int, required: Int)
      val editsWithSize = matches.matches.zipWithIndex.map {
        case (qMatch, matchIndex) =>
          Repetitions(matchIndex, qMatch.tokens.size / childMax,
            if (qMatch.didMatch) 0 else 1)
      }
      val nRepeats = editsWithSize.map(_.repeats).distinct
      val setMinOps = nRepeats.filter(n => n != repeatingOp.min && n <= repeatingOp.max).map { n =>
        (SetMin(queryTokenIndex, n), IntMap(editsWithSize.filter(_.repeats >= n).
          map(x => (x.index, x.required)): _*))
      }
      val setMaxOps = nRepeats.filter(n => n != repeatingOp.max &&
        // SetMax(0) removes the token, which is redundant since we already have RemoveToken ops
        n >= Math.max(1, repeatingOp.min)).map { n =>
        (SetMax(queryTokenIndex, n), IntMap(editsWithSize.filter(_.repeats <= n).
          map(x => (x.index, x.required)): _*))
      }
      val repeatedOps = if (setRepeatedOp) {
        SpecifyingOpGenerator.getRepeatedOpMatch(matches, leafGenerator)
      } else {
        Seq()
      }
      (setMinOps ++ setMaxOps ++ removeOps ++ setChildOps ++ repeatedOps).toMap
    }
  }

  override def generate(
    matches: QueryMatches,
    examples: IndexedSeq[WeightedExample]
  ): Map[QueryOp, IntMap[Int]] = {
    matches.queryToken.qexpr match {
      case None => generateForNone(matches)
      case Some(qd: QDisj) => generateForDisj(qd, matches)
      case Some(qs: QSimilarPhrases) => generateForQSimilarPhrases(qs, matches, examples, None)
      case Some(ql: QLeaf) => generateForQLeaf(ql, matches, false)
      case Some(qr: QRepeating) => generateForQRepeating(qr, matches, examples)
      case _ => Map()
    }
  }
}
