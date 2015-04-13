package org.allenai.dictionary.ml.queryop

import org.allenai.dictionary._
import org.allenai.dictionary.ml.QueryMatches

import scala.collection.immutable.IntMap

/** Builds QueryOps that makes a query more specific (match strictly less sentences)
  *
  * @param suggestPos whether to build operators that add QPos
  * @param suggestWord whether to build operators that add QWord
  * @param clusterSizes what sizes of clusters to suggest adding to the query
  */
case class SpecifyingOpGenerator(
    suggestPos: Boolean,
    suggestWord: Boolean,
    clusterSizes: Seq[Int]
) extends OpGenerator {

  private def getLeafGenerator(
    qexprOption: Option[QExpr],
    isCapture: Boolean
  ): QLeafGenerator = qexprOption match {
    case None => QLeafGenerator(suggestPos, suggestWord, clusterSizes) // Prefix/Suffix slot
    case Some(qexpr) => qexpr match {
      case QPos(_) => QLeafGenerator(pos = false, suggestWord && !isCapture, Seq())
      case QCluster(cluster) =>
        QLeafGenerator(pos = false, suggestWord && !isCapture,
          clusterSizes.filter(_ > cluster.length))
      case QWildcard() => QLeafGenerator(suggestPos, suggestWord && !isCapture, clusterSizes)
      case _ => QLeafGenerator(pos = false, word = false, Seq())
    }
  }

  def generate(matches: QueryMatches): Map[QueryOp, IntMap[Int]] = {
    val index = matches.queryToken.slot.token
    val isCapture = matches.queryToken.isCapture

    matches.queryToken.qexpr match {
      case Some(QPlus(child)) =>
        val removePlus = matches.matches.zipWithIndex.flatMap {
          case (qMatch, matchIndex) =>
            if (qMatch.tokens.size == 1) Some((matchIndex, if (qMatch.didMatch) 0 else 1)) else None
        }
        val setTokenOps = OpGenerator.getSetTokenOps(
          matches, getLeafGenerator(Some(child), isCapture)
        )
        setTokenOps.asInstanceOf[Map[QueryOp, IntMap[Int]]] +
          (RemovePlus(index) -> IntMap(removePlus: _*))
      case Some(QStar(child)) =>
        val editsWithSize = matches.matches.zipWithIndex.map {
          case (qMatch, matchIndex) =>
            (matchIndex, qMatch.tokens.size, if (qMatch.didMatch) 0 else 1)
        }
        val starToPlus = IntMap(editsWithSize.filter(_._2 > 0).map(x => (x._1, x._3)): _*)
        val removeStar = IntMap(editsWithSize.filter(_._2 == 1).map(x => (x._1, x._3)): _*)
        val remove = IntMap(editsWithSize.filter(_._2 == 0).map(x => (x._1, x._3)): _*)
        val setTokenOps = OpGenerator.getSetTokenOps(
          matches, getLeafGenerator(Some(child), isCapture)
        )
        val setTokenOpWithEmpties = setTokenOps.map {
          case (k, v) => (k, v ++ remove)
        }
        setTokenOpWithEmpties.asInstanceOf[Map[QueryOp, IntMap[Int]]] +
          (RemoveStar(index) -> removeStar) +
          (StarToPlus(index) -> starToPlus) +
          (RemoveToken(index) -> remove)
      case other: Option[QExpr] =>
        OpGenerator.getSetTokenOps(matches, getLeafGenerator(other, isCapture)).
          asInstanceOf[Map[QueryOp, IntMap[Int]]]
    }
  }
}
