package org.allenai.dictionary.ml

import org.allenai.dictionary._

case class UnconvertibleQuery(msg: String) extends Exception

object TokenizedQuery {

  def toTokens(qexpr: QExpr): Seq[QExpr] = qexpr match {
    case QSeq(seq) => seq
    case _ => Seq(qexpr)
  }

  def buildFromQuery(qexpr: QExpr): TokenizedQuery = {
    if (QueryLanguage.getQueryLength(qexpr) == -1) {
      throw new UnconvertibleQuery("Query is not fixed length")
    }
    if (QueryLanguage.getCaptureGroups(qexpr).size != 1) {
      throw new UnconvertibleQuery("Query must have exactly one capture group")
    }
    val tokenize = toTokens(qexpr)
    var left = Seq[QExpr]()
    var right = Seq[QExpr]()
    var capture = Seq[QExpr]()
    var onLeft = true
    tokenize.foreach(token => token match {
      case c: QCapture => { capture = toTokens(c.qexpr); onLeft = false }
      case _ if onLeft => left = left :+ token
      case _ => right = right :+ token
    })
    if (capture.size == 0) {
      throw new UnconvertibleQuery("Capture group has size zero")
    }
    TokenizedQuery(left, capture, right)
  }
}

/** Query that has been 'tokenized' into sequences of fixes length tokens.
  *
  * @param left Tokens before the capture group (might be empty)
  * @param capture Tokens in the capture group
  * @param right Tokens after the capture group (might be empty)
  */
case class TokenizedQuery(left: Seq[QExpr], capture: Seq[QExpr], right: Seq[QExpr]) {

  def getQuery: QExpr = {
    QSeq((left :+ QUnnamed(QSeq(capture))) ++ right)
  }

  def getSeq: Seq[QExpr] = {
    (left ++ capture) ++ right
  }
}

