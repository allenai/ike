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
sealed abstract class Generalization()
case class GeneralizeToAny(min: Int, max: Int) extends Generalization
case class GeneralizeToDisj(elements: Seq[QExpr]) extends Generalization
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

  private def getQLeafGeneralizations(
      qexpr: QLeaf,
      searchers: Seq[Searcher],
      sampleSize: Int
      ): Generalization = qexpr match {
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

      val usePos = posCounts.keySet
      Generalization.to(posCounts.keySet.toSeq.map(QPos(_)))
    // Word2Vec also goes here, probably add in the closest N QWords
    case QPos(pos) => Generalization.to(
      (posSets.filter(_.contains(pos)).reduce((a, b) => a ++ b) - pos).map(QPos(_)).toSeq)
    case _ => GeneralizeToNone()
  }

  private def getQDisjGeneralization(
      qexpr: QDisj,
      searchers: Seq[Searcher],
      sampleSize: Int
      ): Generalization = {
    if (qexpr.qexprs.forall(q => q.isInstanceOf[QLeaf]) && qexpr.qexprs.size < 10) {
      val generalizations = qexpr.qexprs.map(q =>
        getQLeafGeneralizations(q.asInstanceOf[QLeaf], searchers, sampleSize))
      val candidatePos = generalizations.map {
        case GeneralizeToDisj(disj) => disj
        case _ => Seq()
      }.flatten.toSet
      Generalization.to((candidatePos -- qexpr.qexprs).toSeq)
    } else {
      val (min, max) = QueryLanguage.getQueryLength(qexpr)
      GeneralizeToAny(min, max)
    }
  }

  /** Suggestions some generalizations for a given query expressions
    *
    * @param qexpr
    * @param searchers
    * @param sampleSize
    * @return Either an Int, indicating that this qexpr could be generalized to match any that many
    *         tokens of any kinds, or a sequence of QExpr that would match generalizations of the
    *         token
    */
  def queryGeneralizations(
      qexpr: QExpr,
      searchers: Seq[Searcher],
      sampleSize: Int
): Generalization = {
    qexpr match {
      case ql: QLeaf => getQLeafGeneralizations(ql, searchers, sampleSize)
      case qd: QDisj => getQDisjGeneralization(qd, searchers, sampleSize)
      case qr: QRepeating if qr.qexpr.isInstanceOf[QLeaf] =>
        val extensions = getQLeafGeneralizations(
          qr.qexpr.asInstanceOf[QLeaf], searchers, sampleSize)
        extensions match {
          case GeneralizeToDisj(disj) => qr.qexpr match {
            case QWord(_) => Generalization.to(disj.map(ex => QRepetition(ex, qr.min, qr.max)))
            case _ => Generalization.to(
              Seq(QRepetition(QDisj(disj :+ qr.qexpr), qr.min, qr.max)))
          }
          case _ => GeneralizeToNone()
        }
      case qr: QRepeating if qr.isInstanceOf[QDisj] =>
        val extensions = getQDisjGeneralization(qr.qexpr.asInstanceOf[QDisj], searchers, sampleSize)
        val atomSize = QueryLanguage.getQueryLength(qr.qexpr)
        extensions match {
          case GeneralizeToAny(min, max) if atomSize == 1 && qr.max == qr.min =>
            GeneralizeToAny(qr.min, qr.max)
          case GeneralizeToDisj(disj) => Generalization.to(
            Seq(QRepetition(QDisj(disj :+ qr.qexpr), qr.min, qr.max)))
          case _ => GeneralizeToNone()
        }
      case _ => GeneralizeToNone()
    }
  }
}
