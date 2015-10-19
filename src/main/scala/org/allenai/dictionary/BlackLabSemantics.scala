package org.allenai.dictionary

import nl.inl.blacklab.search.{
  TextPattern,
  TextPatternAnd,
  TextPatternCaptureGroup,
  TextPatternOr,
  TextPatternProperty,
  TextPatternTerm
}
import nl.inl.blacklab.search.sequences.{
  TextPatternAnyToken,
  TextPatternRepetition,
  TextPatternSequence
}

object BlackLabSemantics {
  var maxRepetition = 128
  def notImplemented: Exception = new UnsupportedOperationException

  // Prefix for auto-generated names for unnamed Capture Groups.
  val genericCaptureGroupNamePrefix = "Capture Group "

  def chunkPatternTerm(p: String) = {
    p match {
      //Covered ("NP", "VP", "PP", "ADJP" , "ADVP")
      case "NP" => new TextPatternOr(
        new TextPatternSequence(
          new TextPatternTerm("B-NP"),
          new TextPatternRepetition(new TextPatternTerm("I-NP"), 0, -1),
          new TextPatternTerm("E-NP")
        ),
        new TextPatternTerm("BE-NP")
      )

      case "VP" => new TextPatternOr(
        new TextPatternSequence(
          new TextPatternTerm("B-VP"),
          new TextPatternRepetition(new TextPatternTerm("I-VP"), 0, -1),
          new TextPatternTerm("E-VP")
        ),
        new TextPatternTerm("BE-VP")
      )

      case "PP" => new TextPatternOr(
        new TextPatternSequence(
          new TextPatternTerm("B-PP"),
          new TextPatternRepetition(new TextPatternTerm("I-PP"), 0, -1),
          new TextPatternTerm("E-PP")
        ),
        new TextPatternTerm("BE-PP")
      )

      case "ADJP" => new TextPatternOr(
        new TextPatternSequence(
          new TextPatternTerm("B-ADJP"),
          new TextPatternRepetition(new TextPatternTerm("I-ADJP"), 0, -1),
          new TextPatternTerm("E-ADJP")
        ),
        new TextPatternTerm("BE-ADJP")
      )

      case "ADVP" => new TextPatternOr(
        new TextPatternSequence(
          new TextPatternTerm("B-ADVP"),
          new TextPatternRepetition(new TextPatternTerm("I-ADVP"), 0, -1),
          new TextPatternTerm("E-ADVP")
        ),
        new TextPatternTerm("BE-ADVP")
      )
    }
  }

  def blackLabQuery(qexpr: QExpr): TextPattern = {
    var unnamedCnt = 0
    def blqHelper(qexpr: QExpr): TextPattern = qexpr match {
      case QWord(w) => new TextPatternTerm(w)
      case QPos(p) => new TextPatternProperty("pos", new TextPatternTerm(p))
      case QChunk(p) => new TextPatternProperty("chunk", chunkPatternTerm(p))
      case QDict(_) =>
        throw new IllegalArgumentException("Can not convert QDict to TextPattern")
      case QNamedPattern(_) =>
        throw new IllegalArgumentException("Can not convert QNamedPattern to TextPattern")
      case QGeneralizePhrase(_, _) =>
        throw new IllegalArgumentException("Can not convert QGeneralizePhrase to TextPattern")
      case QWildcard() => new TextPatternAnyToken(1, 1)
      case QNamed(e: QExpr, name: String) => new TextPatternCaptureGroup(blqHelper(e), name)
      case QUnnamed(e) =>
        unnamedCnt += 1
        val result = blqHelper(QNamed(e, s"$genericCaptureGroupNamePrefix $unnamedCnt"))
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
