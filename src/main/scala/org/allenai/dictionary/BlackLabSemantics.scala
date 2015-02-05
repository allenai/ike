package org.allenai.dictionary

import nl.inl.blacklab.search.Searcher
import nl.inl.blacklab.search.TextPattern
import nl.inl.blacklab.search.TextPatternTerm
import nl.inl.blacklab.search.TextPatternProperty
import nl.inl.blacklab.search.sequences.TextPatternSequence
import nl.inl.blacklab.search.TextPatternRegex
import nl.inl.blacklab.search.TextPatternPrefix
import nl.inl.blacklab.search.sequences.TextPatternAnyToken
import nl.inl.blacklab.search.TextPatternOr
import nl.inl.blacklab.search.sequences.TextPatternRepetition
import nl.inl.blacklab.search.TextPatternCaptureGroup

object BlackLabSemantics {
  def notImplemented: Exception = new UnsupportedOperationException
  def blackLabQuery(qexpr: QExpr): TextPattern = {
    var unnamedCnt = 0
    def blqHelper(qexpr: QExpr): TextPattern = qexpr match {
      case QWord(w) => new TextPatternTerm(w)
      case QCluster(c) => new TextPatternProperty("cluster", new TextPatternPrefix(c))
      case QPos(p) => new TextPatternProperty("pos", new TextPatternTerm(p))
      case QDict(d) => throw notImplemented
      case QWildcard() => new TextPatternAnyToken(1, 1)
      case QNamed(e: QExpr, name: String) => new TextPatternCaptureGroup(blqHelper(e), name)
      case QUnnamed(e) =>
        unnamedCnt += 1
        val result = blqHelper(QNamed(e, s"Capture Group ${unnamedCnt}"))
        result
      case QNonCap(e: QExpr) => blqHelper(e)
      case QStar(e: QExpr) => new TextPatternRepetition(blqHelper(e), 0, -1)
      case QPlus(e: QExpr) => new TextPatternRepetition(blqHelper(e), 1, -1)
      case QSeq(es: Seq[QExpr]) => new TextPatternSequence(es.map(blqHelper): _*)
      case QDisj(es: Seq[QExpr]) => new TextPatternOr(es.map(blqHelper): _*)
      case QClusterFromWord(value, _, _) => blqHelper(QCluster(value))
    }
    blqHelper(qexpr)
  }
}
