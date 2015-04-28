package org.allenai.dictionary.ml

import org.allenai.dictionary._

case class UnconvertibleQuery(msg: String) extends Exception

/** Indicates a 'slot' relative to a particular TokenizedQuery. A Slot indicates a token within
  * the existing query, or an 'empty space' that occurs before of after the existing query
  */
sealed abstract class Slot(val token: Int)

case class Prefix(override val token: Int) extends Slot(token)
case class QueryToken(override val token: Int) extends Slot(token)
case class Suffix(override val token: Int) extends Slot(token)

object QuerySlotData {

  def apply(slot: Prefix): QuerySlotData = {
    QuerySlotData(None, slot, false, false, false)
  }

  def apply(slot: Suffix): QuerySlotData = {
    QuerySlotData(None, slot, false, false, false)
  }

}
/** A 'slot' with a Tokenized query plus some additional data about that slot
  *
  * @param qexpr the QExpr in this slot, None if and only if slot is a Prefix or Suffix
  * @param slot which query-token this data is about
  * @param isCapture whether this slot is contained within a capture group
  * @param leftEdge whether this slot is the left-most non capture group
  * @param rightEdge whether this slot is the right-most non capture group
  */
case class QuerySlotData(qexpr: Option[QExpr], slot: Slot,
    isCapture: Boolean, leftEdge: Boolean, rightEdge: Boolean) {
  require(!(leftEdge && rightEdge))
  if (slot.isInstanceOf[QueryToken]) {
    require(qexpr.isDefined && !((rightEdge || leftEdge) && isCapture))
  } else {
    require(qexpr.isEmpty && !isCapture && !leftEdge && !rightEdge)
  }
}

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

  def getTokenNames(numTokens: Int): Seq[String] = {
    Range(0, numTokens).map(i => s"__QueryToken${i}___")
  }

  private def mergeToAnnotated(
    s1: List[CaptureSequence],
    s2: List[Seq[QExpr]],
    onIndex: Int = 0
  ): Seq[QuerySlotData] = {
    (s1, s2) match {
      case (Nil, end :: Nil) => end.zipWithIndex.map {
        case (qexpr, index) => QuerySlotData(Some(qexpr), QueryToken(index + onIndex + 1),
          false, true, false)
      }
      case (capture :: restCapture, nonCapture :: restNonCapture) =>
        val nonCaptureAnnotated = nonCapture.zipWithIndex.map {
          case (qexpr, index) => QuerySlotData(Some(qexpr), QueryToken(index + onIndex + 1),
            false, onIndex == 0, false)
        }
        val nonCaptureSize = nonCapture.size
        val captureAnnotated = capture.seq.zipWithIndex.map {
          case (qexpr, index) => QuerySlotData(Some(qexpr), QueryToken(index + onIndex +
            nonCaptureSize + 1), true, false, false)
        }
        nonCaptureAnnotated ++ captureAnnotated ++ mergeToAnnotated(
          restCapture, restNonCapture, onIndex + nonCaptureSize + capture.seq.size
        )
      case _ => throw new RuntimeException()
    }
  }

  private def mergeToSeq(s1: List[CaptureSequence], s2: List[Seq[QExpr]]): List[Seq[QExpr]] = {
    (s1, s2) match {
      case (Nil, end :: Nil) => List(end)
      case (capture :: restCapture, nonCapture :: restNonCapture) =>
        List(nonCapture, capture.seq) ++ mergeToSeq(restCapture, restNonCapture)
      case _ => throw new RuntimeException()
    }
  }

  private def mergeToCapturedQExpr(
    s1: List[CaptureSequence], s2: List[Seq[QExpr]], names: List[String]
  ): Seq[QExpr] = {

    def zipToNamed(sq: Seq[QExpr], names: Seq[String]): Seq[QExpr] = {
      sq.zip(names).map {
        case (qexpr, name) =>
          val size = QueryLanguage.getQueryLength(qexpr)
          if (size._1 != size._2 || size._2 == -1) {
            QNamed(qexpr, name)
          } else {
            qexpr
          }
      }
    }

    (s1, s2) match {
      case (Nil, end :: Nil) =>
        require(names.size == end.size); zipToNamed(end, names)
      case (capture :: restCapture, nonCapture :: restNonCapture) =>
        val captureSize = capture.seq.size
        val nonCaptureSize = nonCapture.size
        require(names.size >= captureSize + nonCaptureSize)
        val nonCaptureNamed = zipToNamed(nonCapture, names)
        val captureName = QNamed(TokenizedQuery.qexprFromSequence(
          zipToNamed(capture.seq, names.drop(nonCaptureSize))
        ), capture.columnName)
        val remainingNames = names.drop(nonCaptureSize + captureSize)
        (nonCaptureNamed :+ captureName) ++
          mergeToCapturedQExpr(restCapture, restNonCapture, remainingNames)
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
    * @return the tokenized QExpr
    * @throws IllegalArgumentException if the query capture groups could not be made mapped to
    *                                the table columns
    */
  def buildFromQuery(qexpr: QExpr, tableCols: Seq[String]): TokenizedQuery = {
    buildFromQuery(QueryLanguage.nameCaptureGroups(qexpr, tableCols))
  }

  /** Builds a TokenizedQuery from a QExpr
    *
    * @param qexpr Expression to tokenize, assumed to be fixed length (always matches the same
    *        number of tokens) and with no unnamed capture groups
    * @return the tokenized QExpr
    * @throws IllegalArgumentException if the query has unnamed capture groups
    */
  def buildFromQuery(qexpr: QExpr): TokenizedQuery = {
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

/** Query that has been 'tokenized' into a sequence of QExpr
  *
  * @param captures Sequence of capture groups in the query
  * @param nonCaptures Sequence of of QExpr that occurs between each capture group, including
  *             to the left and right of the first and last capture group. Can contain
  *             empty sequences
  * @throws IllegalArgumentException if capture.size + 1 != nonCapture.size
  */
case class TokenizedQuery(captures: List[CaptureSequence], nonCaptures: List[Seq[QExpr]]) {

  require(captures.size + 1 == nonCaptures.size)

  def size: Int = captures.map(_.seq.size).sum + nonCaptures.map(_.size).sum

  def getQuery: QExpr = {
    TokenizedQuery.qexprFromSequence(TokenizedQuery.mergeToQExpr(captures, nonCaptures))
  }

  def getSeq: Seq[QExpr] = {
    TokenizedQuery.mergeToSeq(captures, nonCaptures).flatten
  }

  /** @return the tokens of this, plus their Slot, their group number, and whether they are part
    *   of a capture group or not
    */
  def getAnnotatedSeq: Seq[QuerySlotData] = {
    TokenizedQuery.mergeToAnnotated(captures, nonCaptures)
  }

  /** @return the names each token in this.getSeq in order */
  def getNames: Seq[String] = {
    TokenizedQuery.getTokenNames(size)
  }

  /** @return the sequence of tokens in this query paired with their names */
  def getNamedTokens: Seq[(String, QExpr)] = {
    getNames.zip(getSeq)
  }

  /** @return this as a query where all of its tokens are in named capture group of their
    * name, in addition to the capture groups normally part of this.getQuery
    */
  def getNamedQuery: QExpr = {
    QSeq(TokenizedQuery.mergeToCapturedQExpr(captures, nonCaptures, getNames.toList))
  }
}
