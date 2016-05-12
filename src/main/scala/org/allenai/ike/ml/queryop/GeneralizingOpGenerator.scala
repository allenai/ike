package org.allenai.ike.ml.queryop

import org.allenai.ike._
import org.allenai.ike.ml.Label._
import org.allenai.ike.ml._

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

  // QLeafs to avoid suggestion if we are making suggestion for QExpr
  private def avoidOp(qexpr: QExpr): Set[QLeaf] = qexpr match {
    case ql: QLeaf => Set(ql)
    case QDisj(disj) => disj.map(avoidOp).reduce(_ ++ _)
    case qr: QRepeating => avoidOp(qr.qexpr)
    case _ => Set()
  }

  private def opsFromGeneralization(
    matches: QueryMatches,
    generalization: GeneralizeToDisj,
    examples: IndexedSeq[WeightedExample]
  ): Map[QueryOp, IntMap[Int]] = {
    val slot = matches.queryToken.slot
    val query = matches.queryToken.qexpr.get
    val posMap = scala.collection.mutable.Map[QPos, List[(Int, Int)]](
      generalization.pos.map((_, List())): _*
    )
    matches.matches.view.zipWithIndex.foreach {
      case (queryMatch, index) =>
        val tokens = queryMatch.tokens
        val pos = tokens.head.pos
        if (posMap.contains(QPos(pos))) {
          posMap(QPos(pos)) = (index, if (queryMatch.didMatch) 0 else 1) :: posMap(QPos(pos))
        }
    }

    val posOps = if (generalization.fullyGeneralizes) {
      // Build SetToken ops
      posMap.toMap.map {
        case (k, v) =>
          val s: QueryOp = SetToken(slot, k)
          (s, IntMap(v.reverse: _*))
      }
    } else {
      // Build AddToken ops, in this case we need to add to ours the sentence the original
      // QueryToken matched since it will continue to match those sentences after the op is applied
      val alreadyMatches = IntMap(
        matches.matches.view.zipWithIndex.filter {
          case (qm, index) => qm.didMatch
        }.map { case (qm, index) => (index, 1) }: _*
      )
      posMap.toMap.flatMap {
        case (k, v) =>
          val qOp: QueryOp = AddToken(slot.token, k)
          // No point in using an AddToken op that does not introduce new positive Hits
          val addsAnyPositive = v.exists {
            case (sentenceIndex, required) =>
              examples(sentenceIndex).label == Positive && required == 1
          }
          if (addsAnyPositive) {
            val newMap = IntMap(v.reverse: _*) ++ alreadyMatches
            Some((qOp, newMap))
          } else {
            None
          }
      }
    }

    val phraseOps = if (generalization.phrase.nonEmpty) {
      val phraseMaps = generalization.phrase.map(new SimilarPhraseMatchTracker(_))
      matches.matches.view.zipWithIndex.foreach {
        case (queryMatch, index) =>
          val phrase = queryMatch.tokens.map(_.word)
          phraseMaps.foreach(_.addPhrase(phrase, queryMatch.didMatch, index))
      }
      val minPosAllowed = query match {
        case qsp: QSimilarPhrases => qsp.pos + 1
        case _ => 1
      }
      val phraseOps = phraseMaps.flatMap { spMatcher =>
        spMatcher.generateOps(minPosAllowed, 100, minSimilarityDifference, examples).map {
          case (threshold, editMap) =>
            if (generalization.fullyGeneralizes) {
              (SetToken(slot, spMatcher.qSimilarPhrases.copy(pos = threshold)), editMap)
            } else {
              (AddToken(slot.token, spMatcher.qSimilarPhrases.copy(pos = threshold)), editMap)
            }
        }
      }
      phraseOps
    } else {
      Seq[(QueryOp, IntMap[Int])]()
    }
    posOps ++ phraseOps.toMap
  }

  override def generate(
    matches: QueryMatches,
    examples: IndexedSeq[WeightedExample]
  ): Map[QueryOp, IntMap[Int]] = {
    val generalizations = matches.queryToken.generalization.get
    generalizations match {
      case GeneralizeToNone() => Map()
      case GeneralizeToAny(_, _) => Map() // We do not handle this during subsampling ATM
      case gd: GeneralizeToDisj => opsFromGeneralization(matches, gd, examples)
    }
  }
}
