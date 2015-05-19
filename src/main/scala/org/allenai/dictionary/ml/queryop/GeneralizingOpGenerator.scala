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
    suggestWord: Boolean,
    createDisjunctions: Boolean
) extends OpGenerator {

  private def allowOps(op: QueryOp, query: QExpr): Boolean = {
    (op, query) match {
      case (_, QDisj(disj)) => disj.exists(allowOps(op, _))
      case (_, QWildcard()) => true
      case (cl: ChangeLeaf, _) =>
        query match {
          case qr: QRepeating if qr.min >= 1 => allowOps(op, qr.qexpr)
          case _ =>
            val changedTo = cl match {
              case SetToken(_, token) => token
              case AddToken(_, token) => token
            }
            changedTo == query
        }
      case (SetMin(_, min), qr: QRepeating) => qr.min < min
      case (SetMax(_, max), qr: QRepeating) => qr.max > max
      case (SetRepeatedToken(slot, index, qexpr), qr: QRepeating)
        if qr.min <= index && qr.max >= index => allowOps(SetToken(slot, qexpr), qr.qexpr)
      case _ => false // Defensively assume if I have not accounted for it its not good
    }
  }

  override def generate(matches: QueryMatches): Map[QueryOp, IntMap[Int]] = {
    val query = matches.queryToken.qexpr.get
    val useAddTokenOps = createDisjunctions || (query match {
      case QDisj(disj) => true
      case qr: QRepeating if qr.qexpr.isInstanceOf[QDisj] => true
      case _ => false
    })
    val slot = matches.queryToken.slot
    require(slot.isInstanceOf[QueryToken] && matches.queryToken.qexpr.isDefined)
    val generalizations = matches.queryToken.generalization.get
    if (generalizations.isInstanceOf[GeneralizeToNone]) {
      Map()
    } else {
      val avoid: Set[QLeaf] = query match {
        case qp: QPos => Set(qp)
        case _ => Set()
      }
      val leaves = QLeafGenerator(pos = true, word = false, avoid)
      def setToken(qexpr: QExpr): Boolean = qexpr match {
        case QPos(_) => true
        case QWord(_) => true
        case qr: QRepeating => setToken(qr.qexpr)
        case _ => false
      }
      val leafOps = if (setToken(query)) {
        OpGenerator.getSetTokenOps(matches, leaves)
      } else {
        Map[QueryOp, IntMap[Int]]()
      }
      val allOps = if (useAddTokenOps) {
        leafOps ++ OpGenerator.getAddTokenOps(matches, leaves)
      } else {
        leafOps
      }
      generalizations match {
        case GeneralizeToAny(_, _) => allOps
        case GeneralizeToDisj(disj) =>
          val allQueries = matches.queryToken.qexpr.get +: disj
          allOps.filter { case (op, _) => allQueries.exists(q => allowOps(op, q)) }
        case GeneralizeToNone() => throw new RuntimeException()
      }
    }
  }
}
