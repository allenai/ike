package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary._
import org.allenai.dictionary.ml._
import org.allenai.dictionary.ml.Label._

import scala.collection.immutable.IntMap

/** Builds QueryOps that make a query more general, so it will match more sentences. The
  * generalization is approximate, so some sentences that were matched originally might no longer
  * match once ops produces by this are applied
  *
  * @param suggestPos whether to build operators that add POS in the query
  * @param suggestWord whether to build operators that add words to the query
  * @param minSimilarityDifference when building possible QSimilarityOps, only suggest ops where
  *                                that have pos values separated by at least this much
  */
case class GeneralizingOpGenerator(
    suggestPos: Boolean,
    suggestWord: Boolean,
    minSimilarityDifference: Int = 0
) extends OpGenerator {

  def buildSimilarPhraseMap(qSimilarPhrases: QSimilarPhrases): SimilarPhraseMap = {
    val similarities = qSimilarPhrases.phrases.sortBy(_.similarity).reverse.zipWithIndex.map {
      case (phrases, index) => phrases.qwords.map(_.value) -> (index + 1)
    }.toMap + (qSimilarPhrases.qwords.map(_.value) -> 0)
    new SimilarPhraseMap(similarities)
  }

  /**
   * Class to keep track of which hits which thresholds of a QSimilarPhrases will match
   *
   * @param similarities Map phrases -> minimum pos needed to match that phrase
   */
  class SimilarPhraseMap(
      val similarities: Map[Seq[String], Int],
      var hits: List[(Int, Int, Int)] = List()) {
    def addPhrase(qm: QueryMatch, index: Int): Unit = {
      val phrase = qm.tokens.map(_.word)
      if (similarities.contains(phrase)) {
        val rank = similarities(phrase)
        hits = (index, if (qm.didMatch) 0 else 1, rank) :: hits
      }
    }
  }

  private def opsFromGeneralization(
      matches: QueryMatches,
      generalization: GeneralizeToDisj,
      examples: IndexedSeq[WeightedExample]
  ): Map[QueryOp, IntMap[Int]] = {
    val slot = matches.queryToken.slot
    val query = matches.queryToken.qexpr.get
    val posMap = scala.collection.mutable.Map[QPos, List[(Int, Int)]](
      generalization.pos.map((_, List())):_*)
    matches.matches.view.zipWithIndex.foreach { case (queryMatch, index) =>
      val tokens = queryMatch.tokens
      val pos = tokens.head.pos
      if (posMap.contains(QPos(pos))) {
        posMap(QPos(pos)) = (index, if (queryMatch.didMatch) 0 else 1) :: posMap(QPos(pos))
      }
    }
    lazy val alreadyMatches = IntMap(matches.matches.view.zipWithIndex.filter {
      case (qm, index) => qm.didMatch
    }.map { case (qm, index) => (index, 1) }:_*)
    val posOps = if (generalization.fullyGeneralizes) {
      posMap.toMap.map { case (k,v) =>
        val s: QueryOp = SetToken(slot, k)
        (s, IntMap(v.reverse:_*)) }
    } else {
      posMap.toMap.map { case (k,v) =>
        val qOp: QueryOp = AddToken(slot.token, k)
        val newMap = IntMap(v.reverse:_*) ++ alreadyMatches
        (qOp, newMap)
      }
    }

    val phraseOps = if (generalization.phrase.nonEmpty) {
      val qsimilarWords = query match {
        case q: QSimilarPhrases => Some(q)
        case _ => None
      }
      val phraseMaps = generalization.phrase.map(buildSimilarPhraseMap)
      matches.matches.view.zipWithIndex.foreach { case (queryMatch, index) =>
        phraseMaps.foreach(_.addPhrase(queryMatch, index))
      }
      val phraseOps = phraseMaps.zip(generalization.phrase).map { case (phraseMaps, qSimilarPhrase) =>
        println(qSimilarPhrase.qwords)
        val min = if (qsimilarWords.isDefined && qsimilarWords.get.qwords ==
            qSimilarPhrase.qwords) {
          qsimilarWords.get.pos
        } else {
          1
        }
        val allHits = phraseMaps.hits.sortBy(_._3)
        var curThreshold = allHits.head._3
        var curLabel: Option[Label] = Some(examples(allHits.head._1).label)
        var prevLabel: Option[Label] = None
        var curMatches: List[(Int, Int)] = List((allHits.head._1, allHits.head._2))
        var prevSize = allHits.count(_._3 == 0)
        var size = 1
        var ops = List[(QueryOp, IntMap[Int])]()
        allHits.drop(1).foreach { case (sentenceIndex, editsMade, minPosThreshold) =>
          val label = examples(sentenceIndex).label
          if (minPosThreshold != curThreshold && curThreshold >= min &&
              (size - prevSize) >= minSimilarityDifference) {
            if (curLabel.isDefined && (curLabel == prevLabel)) {
              // We had two block w/nothing but the same label, drop the previous block
              ops = ops.drop(1)
            }
            val editMap = IntMap(curMatches: _*)
            val queryOp: QueryOp = if (generalization.fullyGeneralizes) {
              SetToken(slot, qSimilarPhrase.copy(pos=curThreshold))
            } else {
              AddToken(slot.token, qSimilarPhrase.copy(pos=curThreshold))
            }
            ops = (queryOp, editMap) :: ops
            prevLabel = curLabel
            curLabel = Some(label)
            prevSize = size
          }
          curLabel = if (curLabel.isDefined && curLabel.get != label) {
            None
          } else {
            curLabel
          }
          size += 1
          curThreshold = minPosThreshold
          curMatches = (sentenceIndex, editsMade) :: curMatches
        }
        // Add the last max POS op
        if ((size - prevSize) >= minSimilarityDifference) {
          if (curLabel.isDefined && (curLabel == prevLabel)) {
            ops = ops.drop(1)
          }
          val queryOp: QueryOp = if (generalization.fullyGeneralizes) {
            SetToken(slot, qSimilarPhrase)
          } else {
            AddToken(slot.token, qSimilarPhrase)
          }
          ops = (queryOp, IntMap(curMatches: _*)) :: ops
        }
        ops.toMap
      }.reduce( (a,b) => a ++ b)
      phraseOps
    } else {
      Map()
    }
    posOps ++ phraseOps
  }

  private def avoidOp(qexpr: QExpr): Set[QLeaf] = qexpr match {
    case ql: QLeaf => Set(ql)
    case QDisj(disj) => disj.map(avoidOp).reduce( (a,b) => a ++ b)
    case qr: QRepeating => avoidOp(qr.qexpr)
    case _ => Set()
  }

  override def generate(matches: QueryMatches, examples: IndexedSeq[WeightedExample]): Map[QueryOp, IntMap[Int]] = {
    val generalizations = matches.queryToken.generalization.get
    generalizations match {
      case GeneralizeToNone() => Map()
      case GeneralizeToAny(_, _) => Map() // We do not handle this ATM
      case gd: GeneralizeToDisj => opsFromGeneralization(matches, gd, examples)
    }
  }
}
