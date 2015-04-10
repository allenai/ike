package org.allenai.dictionary.ml

import org.allenai.dictionary._

case class UnconvertibleQuery(msg: String) extends Exception

/** A sequence of of QExpr 'tokens' that make up one named capture group
  * paired with the name of the capture group
  */
case class CaptureSequence(seq: Seq[QExpr], columnName: String) {
  require(seq.nonEmpty)
  def getQuery: QCapture = {
    QNamed(TokenizedQuery.qexprFromSequence(seq), columnName)
  }
}

object TokenizedQuery {

  private def mergeToSeq(s1: List[CaptureSequence], s2: List[Seq[QExpr]]): List[Seq[QExpr]] = {
    (s1, s2) match {
      case (Nil, end :: Nil) => List(end)
      case (capture :: restCapture, nonCapture :: restNonCapture) =>
        List(nonCapture, capture.seq) ++ mergeToSeq(restCapture, restNonCapture)
      case _ => throw new RuntimeException()
    }
  }

  private def mergeToQExpr(s1: List[CaptureSequence], s2: List[Seq[QExpr]]): List[QExpr] = {
    (s1, s2) match {
      case (Nil, end :: Nil) => end.toList
      case (capture :: restCapture, nonCapture :: restNonCapture) =>
        nonCapture.toList ++ (capture.getQuery +: mergeToQExpr(restCapture, restNonCapture))
      case _ => throw new RuntimeException()
    }
  }

  def qexprToSequence(qexpr: QExpr): Seq[QExpr] = qexpr match {
    case QSeq(seq) => seq
    case _ => Seq(qexpr)
  }

  def qexprFromSequence(qepxr: Seq[QExpr]): QExpr = {
    if (qepxr.size == 1) qepxr.head else QSeq(qepxr)
  }

  /** Builds a TokenizedQuery from a QExpr
    *
    * @param qexpr Expression to tokenize
    * @param tableCols Sequence of table columns, used to name the query's unnamed capture group
    * @throws UnconvertibleQuery if the query was not fixed length
    * @return the tokenized QExpr
    */
  def buildFromQuery(qexpr: QExpr, tableCols: Seq[String]): TokenizedQuery = {
    buildFromQuery(QueryLanguage.nameCaptureGroups(qexpr, tableCols))
  }

  /** Builds a TokenizedQuery from a QExpr
    *
    * @param qexpr Expression to tokenize, assumed to be fixed length (always matches the same
    *             number of tokens) and with no unnamed capture groups
    * @return the tokenized QExpr
    * @throws UnconvertibleQuery if the query was not fixed length
    * @throws IllegalArgumentException if the query has unnamed capture groups
    */
  def buildFromQuery(qexpr: QExpr): TokenizedQuery = {
    if (QueryLanguage.getQueryLength(qexpr) == -1) {
      throw new UnconvertibleQuery("Query is not fixed length")
    }

    val asSeq = qexprToSequence(qexpr)

    var captures = List[CaptureSequence]()
    var currentNonCapture = List[QExpr]()
    var nonCaptures = List[Seq[QExpr]]()
    asSeq.foreach {
      case c: QCapture =>
        nonCaptures = currentNonCapture.reverse :: nonCaptures
        currentNonCapture = List[QExpr]()
        val captureSeq = c match {
          case QUnnamed(expr) => throw new IllegalArgumentException("Cannot convert unnamed " +
            "capture groups")
          case QNamed(expr, name) => CaptureSequence(qexprToSequence(expr), name)
        }
        captures = captureSeq :: captures
      case q: QExpr => currentNonCapture = q :: currentNonCapture
    }
    nonCaptures = currentNonCapture.reverse :: nonCaptures
    TokenizedQuery(captures.reverse, nonCaptures.reverse)
  }
}

/** Query that has been 'tokenized' into sequences of fixed length tokens
  *
  * @param captures Sequence of capture groups in the query
  * @param nonCaptures Sequence of of QExpr that occurs between each capture group, including
  *                  to the left and right of the first and last capture group. Can contain
  *                  empty sequences
  * @throws IllegalArgumentException if capture.size + 1 != nonCapture.size
  */
case class TokenizedQuery(captures: List[CaptureSequence], nonCaptures: List[Seq[QExpr]]) {

  require(captures.size + 1 == nonCaptures.size)

  def getQuery: QExpr = {
    TokenizedQuery.qexprFromSequence(TokenizedQuery.mergeToQExpr(captures, nonCaptures))
  }

  def getSeq: Seq[QExpr] = {
    TokenizedQuery.mergeToSeq(captures, nonCaptures).flatten
  }
}

