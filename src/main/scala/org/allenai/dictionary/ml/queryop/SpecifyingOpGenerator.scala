package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary._
import org.allenai.dictionary.ml.{WeightedExample, QueryMatches}

import scala.collection.immutable.IntMap

/** Builds QueryOps that makes a query more specific (match strictly less sentences)
  *
  * @param suggestPos whether to build operators that add QPos
  * @param suggestWord whether to build operators that add QWord
  */
case class SpecifyingOpGenerator(
    suggestPos: Boolean,
    suggestWord: Boolean,
    setRepeatedOp: Boolean = false
) extends OpGenerator {

  private def getLeafGenerator(
    qexprOption: Option[QExpr],
    isCapture: Boolean
  ): QLeafGenerator = qexprOption match {
    case None => QLeafGenerator(suggestPos, suggestWord) // Prefix/Suffix slot
    case Some(qexpr) => qexpr match {
      case QPos(_) => QLeafGenerator(pos = false, suggestWord && !isCapture)
      case QWildcard() => QLeafGenerator(suggestPos, suggestWord && !isCapture)
      case _ => QLeafGenerator(pos = false, word = false)
    }
  }

  def generateForQLeaf(qleafOpt: Option[QLeaf], matches: QueryMatches):
    Map[QueryOp, IntMap[Int]] = {
    val isCapture = matches.queryToken.isCapture
    val tmp = OpGenerator.getSetTokenOps(matches, getLeafGenerator(qleafOpt, isCapture))
    tmp
  }

  def generateForQRepeating(repeatingOp: QRepeating, matches: QueryMatches):
  Map[QueryOp, IntMap[Int]] = {
    val index = matches.queryToken.slot.token
    val isCapture = matches.queryToken.isCapture
    val (childMin, childMax) = QueryLanguage.getQueryLength(repeatingOp.qexpr)
    if (childMin != childMax) {
      Map()
    } else {
      case class Repetitions(index: Int, repeats: Int, required: Int)
      val editsWithSize = matches.matches.zipWithIndex.map {
        case (qMatch, matchIndex) =>
          Repetitions(matchIndex, qMatch.tokens.size / childMax,
            if (qMatch.didMatch) 0 else 1)
      }
      val removeOps =
        if (repeatingOp.min == 0) {
          Seq((
              RemoveToken(index),
              IntMap(editsWithSize.filter(_.repeats == 0).map(x => (x.index, x.required)): _*)
              ))
        } else {
          Seq()
        }
      val nRepeats = editsWithSize.map(_.repeats).distinct
      val setMinOps = nRepeats.filter(_ != repeatingOp.min).map { n =>
        (SetMin(index, n), IntMap(editsWithSize.filter(_.repeats >= n).
            map(x => (x.index, x.required)): _*))
      }.filter(_._1.min <= repeatingOp.max)
      val setMaxOps = nRepeats.filter(_ != repeatingOp.max).map { n =>
        (SetMax(index, n), IntMap(editsWithSize.filter(_.repeats <= n).
            map(x => (x.index, x.required)): _*))
      }.filter(_._1.max >= repeatingOp.min)
      val leafGenerator = getLeafGenerator(Some(repeatingOp.qexpr), isCapture)
      val leafOps = OpGenerator.getSetTokenOps(matches, leafGenerator).
          asInstanceOf[Map[QueryOp, IntMap[Int]]]
      val repeatedOps = if (setRepeatedOp) {
        OpGenerator.getRepeatedOpMatch(matches, leafGenerator)
      } else {
        Seq()
      }
      (setMinOps ++ setMaxOps ++ removeOps ++ leafOps ++ repeatedOps).toMap
    }
  }

  override def generate(matches: QueryMatches, examples: IndexedSeq[WeightedExample]):
  Map[QueryOp, IntMap[Int]] = {
    matches.queryToken.qexpr match {
      case None => generateForQLeaf(None, matches)
      case Some(ql: QLeaf) => generateForQLeaf(Some(ql), matches)
      case Some(qr: QRepeating) => generateForQRepeating(qr, matches)
      case _ => Map()
    }
  }
}
