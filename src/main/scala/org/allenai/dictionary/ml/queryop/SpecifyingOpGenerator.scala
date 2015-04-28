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
    val expr = matches.queryToken.qexpr

    if (expr.isEmpty || !expr.get.isInstanceOf[QRepeating]) {
      OpGenerator.getSetTokenOps(matches, getLeafGenerator(expr, isCapture)).
        asInstanceOf[Map[QueryOp, IntMap[Int]]]
    } else {
      val repeatingOp = expr.get.asInstanceOf[QRepeating]
      val childLength = QueryLanguage.getQueryLength(repeatingOp.qexpr)._2
      if (childLength == -1) {
        Map()
      } else {
        case class Repetitions(index: Int, repeats: Int, required: Int)
        val editsWithSize = matches.matches.zipWithIndex.map {
          case (qMatch, matchIndex) =>
            Repetitions(matchIndex, qMatch.tokens.size / childLength,
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
        val nRepeats = editsWithSize.map(_.repeats).toSet.toSeq
        val setMinOps = nRepeats.filter(_ != repeatingOp.min).map { n =>
          (SetMin(index, n), IntMap(editsWithSize.filter(_.repeats >= n).
            map(x => (x.index, x.required)): _*))
        }
        val setMaxOps = nRepeats.filter(_ != repeatingOp.max).map { n =>
          (SetMax(index, n), IntMap(editsWithSize.filter(_.repeats <= n).
            map(x => (x.index, x.required)): _*))
        }

        val leafOps = OpGenerator.getSetTokenOps(matches, getLeafGenerator(
          Some(repeatingOp.qexpr),
          isCapture
        )).asInstanceOf[Map[QueryOp, IntMap[Int]]]

        (setMinOps ++ setMaxOps ++ removeOps ++ leafOps).toMap
      }
    }
  }
}
