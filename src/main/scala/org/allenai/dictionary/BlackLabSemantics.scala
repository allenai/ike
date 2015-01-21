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

case class BlackLabSemantics(searcher: Searcher) {
  def notImplemented: Exception = new UnsupportedOperationException
  def blackLabQuery(qexpr: QExpr): TextPattern = qexpr match {
    case QWord(w) => new TextPatternTerm(w)
    case QCluster(c) => new TextPatternProperty("cluster", new TextPatternPrefix(c))
    case QPos(p) => new TextPatternProperty("pos", new TextPatternTerm(p))
    case QDict(d) => throw notImplemented
    case QWildcard => new TextPatternAnyToken(1, 1)
    case QNamed(e: QExpr, name: String) => new TextPatternCaptureGroup(blackLabQuery(e), name)
    case QUnnamed(e: QExpr) => blackLabQuery(QNamed(e, s"pos${e.pos.toString}"))
    case QNonCap(e: QExpr) => blackLabQuery(e)
    case QStar(e: QExpr) => new TextPatternRepetition(blackLabQuery(e), 0, -1)
    case QPlus(e: QExpr) => new TextPatternRepetition(blackLabQuery(e), 1, -1)
    case QSeq(es: Seq[QExpr]) => new TextPatternSequence(es.map(blackLabQuery):_*)
    case QDisj(es: Seq[QExpr]) => new TextPatternOr(es.map(blackLabQuery):_*)
  }
}
