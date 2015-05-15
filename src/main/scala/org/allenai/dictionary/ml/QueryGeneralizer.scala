package org.allenai.dictionary.ml

import nl.inl.blacklab.search.Searcher
import org.allenai.dictionary._

import scala.collection.JavaConverters._

object Generalization {
  def to(elements: Seq[QExpr]): Generalization = {
    if (elements.isEmpty) {
      GeneralizeToNone()
    } else {
      GeneralizeToDisj(elements)
    }
  }
}

/** Represents a way of generalizing another QExpr*/
sealed abstract class Generalization()

/** Use any query of the given length */
case class GeneralizeToAny(min: Int, max: Int) extends Generalization

/** Use a query from a fixed set */
case class GeneralizeToDisj(elements: Seq[QExpr]) extends Generalization

/** No generalizations possible */
case class GeneralizeToNone() extends Generalization

object QueryGeneralizer {
  // TODO make this a class, cache the results per token

  val posSets = Seq(
    Set("VBZ", "VBP", "VBN", "VBG", "VBD", "VB"),
    Set("NNPS", "NN", "NNP", "NNS"),
    Set("PRP$", "PRP", "DT", "PDT", "EX", "MD", "LS"),
    Set("JJS", "JJR", "JJ", "RB", "IN", "DT", "PDT", "CC", "CD", "TO",
      "UH", "SYM", "POS", "PRP", "PDT", "EX", "MD", "LS"),
    Set("WRB", "WP$", "WDT", "WP"),
    Set("RBS", "RBR", "RP", "SYM", "RB", "IN", "CD", "MD")
  ).map(_ + "FW")

  /** Suggestions some generalizations for a given query expressions
    *
    * @param qexpr QExpr to generalize
    * @param searchers Searchers to use when deciding what to generalize
    * @param sampleSize Number of samples to get per a searcher when deciding what a word can be
    *                   generalized to
    * @return Generalization that could be made from the QExpr
    */
  def queryGeneralizations(
      qexpr: QExpr,
      searchers: Seq[Searcher],
      sampleSize: Int
): Generalization = {
    qexpr match {
      case QWord(word) =>
        val posTags = searchers.map { searcher =>
          val hits = searcher.find(BlackLabSemantics.blackLabQuery(qexpr)).window(0, sampleSize)
          hits.setContextSize(0)
          hits.setContextField(List("pos").asJava)
          hits.asScala.map { hit =>
            val kwic = hits.getKwic(hit)
            val pos = kwic.getTokens("pos").get(0)
            pos
          }
        }.flatten
        val posCounts = posTags.groupBy(identity).mapValues(_.size)
        Generalization.to(posCounts.keySet.toSeq.map(QPos(_)))
      case QPos(pos) => Generalization.to(
        (posSets.filter(_.contains(pos)).reduce((a, b) => a ++ b) - pos).map(QPos(_)).toSeq)
      case QDisj(qexprs) =>
        if (qexprs.size < 10) {
          val generalizations = qexprs.map(queryGeneralizations(_, searchers, sampleSize))
          val candidatePos = generalizations.map {
            case GeneralizeToDisj(disj) => disj
            case _ => Seq()
          }.flatten.toSet
          Generalization.to((candidatePos -- qexprs).toSeq)
        } else {
          val (min, max) = QueryLanguage.getQueryLength(qexpr)
          GeneralizeToAny(min, max)
        }
      case qr: QRepeating => {
        val childGeneralization = queryGeneralizations(qr.qexpr, searchers, sampleSize)
        childGeneralization match {
          case GeneralizeToDisj(elements) =>
            GeneralizeToDisj(elements.map(QRepetition(_, qr.min, qr.max)))
          case GeneralizeToAny(min, max) if min == 1 =>
            // if min != 1 then QRepeating(childGeneralization) can only match sequences of
            // particular size (ex. sequence of size 2,4,6...) which we currently cant model
            GeneralizeToAny(min * qr.min, if (qr.max == -1) -1 else qr.max * max)
          case _ => GeneralizeToNone()
        }
      }
      case _ => GeneralizeToNone()
    }
  }
}
