package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary._
import org.allenai.dictionary.ml._

import scala.collection.immutable.IntMap

/** Builds QueryOps that make a query more general, so it will match more sentences. The
  * generalization is approximate, so some sentences that were matched originally might no longer
  * match once ops produces by this are applied
  *
  * @param suggestPos whether to build operators that add POS in the query
  * @param suggestWord whether to build operators that add words to the query
  */
case class GeneralizingOpGenerator(
    suggestPos: Boolean,
    suggestWord: Boolean
) extends OpGenerator {

  /* Builds a QLeafGenerator to determine what QLeaf to use in SetTokenOps for the
   * the given query expression
   */
  private def getSetTokenLeaves(qexpr: QExpr, isCapture: Boolean): QLeafGenerator = {
    qexpr match {
      case QWord(_) => QLeafGenerator(suggestPos, word = false)
      case QPos(_) => QLeafGenerator(pos = false, word = false)
      case _ => QLeafGenerator(pos = false, word = false)
    }
  }

  /* Builds a QLeafGenerator to determine what QLeaf to use in AddTokenOps for the
   * the given query expression
   */
  //  private def getAddTokenLeaves(qexpr: QExpr, isCapture: Boolean): QLeafGenerator = {
  //    qexpr match {
  //      case q: QWord => QLeafGenerator(pos = false, !isCapture, Set(q))
  //      case q: QPos => QLeafGenerator(suggestWord, word = false, Set(q))
  //      case QDisj(qexprs) =>
  //        val avoid = qexprs.flatMap {
  //          case q: QLeaf => Some(q)
  //          case _ => None
  //        }.toSet
  //        val anyPos = qexprs.forall(!_.isInstanceOf[QPos])
  //        val anyWord = qexprs.forall(!_.isInstanceOf[QWord])
  //        QLeafGenerator(
  //          (!anyPos || anyWord) && suggestPos,
  //          (anyPos || !anyWord) && suggestWord, avoid
  //        )
  //      case _ => QLeafGenerator(pos = false, word = false)
  //    }
  //  }

  override def generate(matches: QueryMatches): Map[QueryOp, IntMap[Int]] = {
    val slot = matches.queryToken.slot
    require(slot.isInstanceOf[QueryToken] && matches.queryToken.qexpr.isDefined)
    val generalizations = matches.queryToken.generalization.get
    if (generalizations.isInstanceOf[GeneralizeToNone]) {
      Map()
    } else {
      val leaves = getSetTokenLeaves(matches.queryToken.qexpr.get, matches.queryToken.isCapture)
      val leaveOps = OpGenerator.getSetTokenOps(matches, leaves)

      val setLeaves = if (generalizations.isInstanceOf[GeneralizeToAny]) {
        leaveOps
      } else if (generalizations.isInstanceOf[GeneralizeToDisj]) {
        val allowedOps = generalizations.asInstanceOf[GeneralizeToDisj].elements.toSet
        leaveOps.filterKeys(allowedOps contains _.qexpr)
      } else {
        throw new RuntimeException()
      }
      setLeaves.asInstanceOf[Map[QueryOp, IntMap[Int]]]
    }
  }
}
