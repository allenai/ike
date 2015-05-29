package org.allenai.dictionary.ml

import nl.inl.blacklab.search.Searcher
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
  */
// TODO prefix/suffix should probably be their own classes
case class QuerySlotData(qexpr: Option[QExpr], slot: Slot, isCapture: Boolean,
    firstTokenSequence: Boolean, lastTokenSequence: Boolean,
    generalization: Option[Generalization] = None) {
  if (slot.isInstanceOf[QueryToken]) {
    require(qexpr.isDefined)
  } else {
    require(qexpr.isEmpty && generalization.isEmpty &&
      !isCapture && !firstTokenSequence && !lastTokenSequence)
  }
}

/** A sequence of of QExpr 'tokens' that make up one named capture group
  * paired with the name of the capture group
  */
case class QueryTokenSequence(queryTokens: Seq[QExpr], columnName: Option[String]) {
  def getQuery: QExpr = {
    if (columnName.isDefined) {
      QNamed(TokenizedQuery.qexprFromSequence(queryTokens), columnName.get)
    } else {
      TokenizedQuery.qexprFromSequence(queryTokens)
    }
  }
  def size: Int = queryTokens.size
  def isCaptureGroup: Boolean = columnName.isDefined
}

object TokenizedQuery {

  def getTokenName(tokenIndex: Int): String = s"___QueryToken${tokenIndex}___"
  def getTokenNames(numTokens: Int): Seq[String] = Range(0, numTokens).map(getTokenName)

  def flattenQExpr(qexpr: QExpr): Seq[QExpr] = qexpr match {
    case QSeq(seq) =>
      if (seq.isEmpty) {
        Seq()
      } else if (seq.size == 1) {
        flattenQExpr(seq.head)
      } else {
        seq.flatMap(flattenQExpr)
      }
    case QDisj(seq) =>
      if (seq.isEmpty) {
        Seq()
      } else if (seq.size == 1) {
        flattenQExpr(seq.head)
      } else {
        Seq(QDisj(seq))
      }
    case QNonCap(qexpr) => flattenQExpr(qexpr)
    case _ => Seq(qexpr)
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
    *                             the table columns
    */
  def buildFromQuery(qexpr: QExpr, tableCols: Seq[String]): TokenizedQuery = {
    buildFromQuery(QueryLanguage.nameCaptureGroups(qexpr, tableCols))
  }

  /** Builds a TokenizedQuery from a QExpr
    *
    * @param qexpr Expression to tokenize, assumed to be fixed length (always matches the same
    *     number of tokens) and with no unnamed capture groups
    * @return the tokenized QExpr
    * @throws IllegalArgumentException if the query has unnamed capture groups
    */
  def buildFromQuery(qexpr: QExpr): TokenizedQuery = {
    val asSeq = flattenQExpr(qexpr)

    var tokenSequences = Seq[QueryTokenSequence]()
    var onSequence = List[QExpr]()
    asSeq.foreach {
      case c: QCapture =>
        if (onSequence.nonEmpty) {
          tokenSequences = tokenSequences :+ QueryTokenSequence(onSequence.reverse, None)
          onSequence = List()
        }
        val captureSeq = c match {
          case QUnnamed(expr) => throw new IllegalArgumentException("Cannot convert unnamed " +
            "capture groups")
          case QNamed(expr, name) => QueryTokenSequence(flattenQExpr(expr), Some(name))
        }
        tokenSequences = tokenSequences :+ captureSeq
      case q: QExpr => onSequence = q :: onSequence
    }
    if (onSequence.nonEmpty) {
      tokenSequences = tokenSequences :+ QueryTokenSequence(onSequence.reverse, None)
    }
    tokenSequences.foreach(ts => require(ts.queryTokens.nonEmpty))
    TokenizedQuery(tokenSequences)
  }

  def buildWithGeneralizations(
    qexpr: QExpr,
    searchers: Seq[Searcher],
    similarPhrasesSearcher: SimilarPhrasesSearcher,
    sampleSize: Int
  ): TokenizedQuery = {
    val tq = buildFromQuery(qexpr)
    val generalizations =
      tq.getSeq.map(QueryGeneralizer.queryGeneralizations(_, searchers,
        similarPhrasesSearcher, sampleSize))
    tq.copy(generalizations = Some(generalizations))
  }
}

/** Query that has been 'tokenized' into a sequence of QExpr, the QExpr are then chunked together
  * into tokenSequence which can be marked as capture groups or not.
  *
  * @param tokenSequences
  * @param generalizations
  * @throws IllegalArgumentException if capture.size + 1 != nonCapture.size
  */
case class TokenizedQuery(
    tokenSequences: Seq[QueryTokenSequence],
    generalizations: Option[Seq[Generalization]] = None
) {

  val size: Int = tokenSequences.map(_.queryTokens.size).sum
  require(generalizations.isEmpty || size == generalizations.get.size)

  def getQuery: QExpr = {
    TokenizedQuery.qexprFromSequence(tokenSequences.map { ts =>
      if (ts.isCaptureGroup) {
        Seq(ts.getQuery)
      } else {
        ts.queryTokens
      }
    }.flatten)
  }

  def getSeq: Seq[QExpr] = {
    tokenSequences.map(_.queryTokens).flatten
  }

  def getCaptureGroups: Seq[(String, Seq[QExpr])] = tokenSequences.filter(_.isCaptureGroup).map {
    qc => (qc.columnName.get, qc.queryTokens)
  }

  /** @return the tokens of this, plus their Slot, their group number, and whether they are part
    * of a capture group or not
    */
  def getAnnotatedSeq: Seq[QuerySlotData] = {
    var onIndex = 0
    tokenSequences.zipWithIndex.map {
      case (tokenSequence, sequenceIndex) =>
        val isCapture = tokenSequence.columnName.isDefined
        tokenSequence.queryTokens.map { queryToken =>
          val gen = if (generalizations.isDefined) {
            Some(generalizations.get(onIndex))
          } else {
            None
          }
          val qsd = QuerySlotData(Some(queryToken), QueryToken(onIndex + 1), isCapture,
            sequenceIndex == 0, sequenceIndex == tokenSequences.size - 1, gen)
          onIndex += 1
          qsd
        }
    }.flatten
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
    var onIndex = 0
    val querySequence = tokenSequences.map { tokenSequence =>
      val namedTokens = tokenSequence.queryTokens.map { queryToken =>
        val (min, max) = QueryLanguage.getQueryLength(queryToken)
        val q = if (min == max) {
          queryToken
        } else {
          QNamed(queryToken, TokenizedQuery.getTokenName(onIndex))
        }
        onIndex += 1
        q
      }
      val query = TokenizedQuery.qexprFromSequence(namedTokens)
      if (tokenSequence.columnName.isDefined) {
        QNamed(query, tokenSequence.columnName.get)
      } else {
        query
      }
    }
    TokenizedQuery.qexprFromSequence(querySequence)
  }
}
