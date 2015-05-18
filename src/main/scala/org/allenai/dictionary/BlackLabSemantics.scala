package org.allenai.dictionary

import nl.inl.blacklab.search.{ TextPattern, TextPatternAnd, TextPatternCaptureGroup, TextPatternOr, TextPatternPrefix, TextPatternProperty, TextPatternTerm }
import nl.inl.blacklab.search.sequences.{ TextPatternAnyToken, TextPatternRepetition, TextPatternSequence }

object BlackLabSemantics {
  var maxRepetition = 128
  def notImplemented: Exception = new UnsupportedOperationException
  def blackLabQuery(qexpr: QExpr): TextPattern = {
    var unnamedCnt = 0
    def blqHelper(qexpr: QExpr): TextPattern = qexpr match {
      case QWord(w) => new TextPatternTerm(w)
      case QPos(p) => new TextPatternProperty("pos", new TextPatternTerm(p))
      case QDict(d) => throw notImplemented
      case QWildcard() => new TextPatternAnyToken(1, 1)
      case QNamed(e: QExpr, name: String) => new TextPatternCaptureGroup(blqHelper(e), name)
      case QUnnamed(e) =>
        unnamedCnt += 1
        val result = blqHelper(QNamed(e, s"Capture Group ${unnamedCnt}"))
        result
      case QNonCap(e: QExpr) => blqHelper(e)
      case QStar(e: QExpr) => new TextPatternRepetition(blqHelper(e), 0, maxRepetition)
      case QPlus(e: QExpr) => new TextPatternRepetition(blqHelper(e), 1, maxRepetition)
      case QRepetition(e, min, max) => new TextPatternRepetition(blqHelper(e), min, max)
      case QSeq(es: Seq[QExpr]) => new TextPatternSequence(es.map(blqHelper): _*)
      case QDisj(es: Seq[QExpr]) => new TextPatternOr(es.map(blqHelper): _*)
      case QAnd(expr1, expr2) => new TextPatternAnd(blqHelper(expr1), blqHelper(expr2))
      case QPosFromWord(value, word, posTags) => value match {
        case Some(string) => blqHelper(QPos(string))
        case None => blqHelper(QWord(word))
      }
      case QSimilarPhrases(qwords, pos, phrases) => {
        val selected = phrases.slice(0, pos)
        val seqs = QSeq(qwords) +: selected.map(p => QSeq(p.qwords))
        val disj = QDisj(seqs)
        blqHelper(disj)
      }
    }
    blqHelper(qexpr)
  }
}
