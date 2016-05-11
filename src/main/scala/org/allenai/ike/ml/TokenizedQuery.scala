package org.allenai.ike.ml

import org.allenai.ike._

import nl.inl.blacklab.search.Searcher

/** Indicates a 'slot' relative to a particular TokenizedQuery. A Slot indicates a token within
  * the existing query, or an 'empty space' that occurs before of after the existing query
  */
sealed abstract class Slot(val token: Int)

case class Prefix(override val token: Int) extends Slot(token)
case class QueryToken(override val token: Int) extends Slot(token)
case class Suffix(override val token: Int) extends Slot(token)

object QuerySlotData {

  def apply(slot: Prefix): QuerySlotData = {
    QuerySlotData(None, slot, false)
  }

  def apply(slot: Suffix): QuerySlotData = {
    QuerySlotData(None, slot, false)
  }

}
/** A 'slot' with a Tokenized query plus some additional data about that slot
  *
  * @param qexpr the QExpr in this slot, None if and only if slot is a Prefix or Suffix
  * @param slot which query-token this data is about
  * @param isCapture whether this slot is contained within a capture group
  */
case class QuerySlotData(qexpr: Option[QExpr], slot: Slot, isCapture: Boolean,
    generalization: Option[Generalization] = None) {
  if (slot.isInstanceOf[QueryToken]) {
    require(qexpr.isDefined)
  } else {
    require(qexpr.isEmpty && generalization.isEmpty && !isCapture)
  }
}

/** A 'chunk' of QueryTokens that may or may not be part of a capture group */
sealed trait QueryTokenSequence {
  def queryTokens: Seq[QExpr]
  def getOriginalQuery: QExpr
  def size: Int = queryTokens.size
}
case class TokenSequence(queryTokens: Seq[QExpr]) extends QueryTokenSequence {
  override def getOriginalQuery: QExpr = TokenizedQuery.qexprFromSequence(queryTokens)
}
case class CapturedTokenSequence(
    queryTokens: Seq[QExpr],
    captureName: String, wasExplicitlyNamed: Boolean
) extends QueryTokenSequence {
  override def getOriginalQuery: QExpr = {
    if (wasExplicitlyNamed) {
      QNamed(TokenizedQuery.qexprFromSequence(queryTokens), captureName)
    } else {
      QUnnamed(TokenizedQuery.qexprFromSequence(queryTokens))
    }
  }
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
    case QNonCap(child) => flattenQExpr(child)
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
    * @param qexpr QExpr to tokenize
    * @param tableCols tables columns to use when naming unnamed capture groups
    * @return the tokenized QExpr
    */
  def buildFromQuery(qexpr: QExpr, tableCols: Seq[String]): TokenizedQuery = {
    val asSeq = flattenQExpr(qexpr)

    var columnNamesLeft = tableCols
    var tokenSequences = Seq[QueryTokenSequence]()
    var onSequence = List[QExpr]()
    asSeq.foreach {
      case c: QCapture =>
        if (onSequence.nonEmpty) {
          tokenSequences = tokenSequences :+ TokenSequence(onSequence.reverse)
          onSequence = List()
        }
        val columnName = c match {
          case QUnnamed(expr) =>
            require(columnNamesLeft.nonEmpty, "Query has more captures groups then there are " +
              "table columns")
            val name = columnNamesLeft.head
            columnNamesLeft = columnNamesLeft.tail
            name
          case QNamed(expr, name) =>
            require(columnNamesLeft.contains(name), "Could not find a table column corresponding " +
              s"to capture group $name")
            columnNamesLeft = columnNamesLeft.filter(_ != name)
            name
        }
        tokenSequences = tokenSequences :+ CapturedTokenSequence(
          flattenQExpr(c.qexpr), columnName, c.isInstanceOf[QNamed]
        )
      case q: QExpr => onSequence = q :: onSequence
    }
    if (onSequence.nonEmpty) {
      tokenSequences = tokenSequences :+ TokenSequence(onSequence.reverse)
    }

    if ((tableCols.size == 1) &&
      tokenSequences.size == 1 && tokenSequences.head.isInstanceOf[TokenSequence]) {
      // No capture groups and one column table, assume the whole query was intended to be captured
      tokenSequences =
        Seq(CapturedTokenSequence(tokenSequences.head.queryTokens, tableCols.head, false))
    } else {
      require(columnNamesLeft.isEmpty, s"Could not align capture groups for " +
        s"${QueryLanguage.getQueryString(qexpr)} with columns $tableCols")
    }
    tokenSequences.foreach(ts => require(ts.queryTokens.nonEmpty))
    TokenizedQuery(tokenSequences)
  }

  /** Builds a TokenizedQuery with generalizations included
    *
    * @param qexpr QExpr to tokenize
    * @param tableCols tables columns to use when naming unnamed capture groups
    * @param similarPhrasesSearcher to use when generalizing tokens
    * @param searchers to use when deciding how to generalize words to POS tags
    * @param posSampleSize sample size to use when deciding how to generalize words to POS tags
    * @return the tokenized QExpr
    */
  def buildWithGeneralizations(
    qexpr: QExpr,
    tableCols: Seq[String],
    similarPhrasesSearcher: SimilarPhrasesSearcher,
    searchers: Seq[Searcher],
    posSampleSize: Int
  ): TokenizedQuery = {
    val tq = buildFromQuery(qexpr, tableCols)
    val generalizations =
      tq.getSeq.map(QueryGeneralizer.queryGeneralizations(_, searchers,
        similarPhrasesSearcher, posSampleSize))

    // Special case when we have one word, only use 'phrase' generalization. Use POS
    // generalizations here will make the POS matches dominate our sample and thus reduce our
    // ability to select good phrase generalizations.
    val phraseOnlyGeneralizations = generalizations match {
      case Seq(GeneralizeToDisj(pos, phrase, fullyGeneralizes)) if phrase.nonEmpty =>
        Seq(GeneralizeToDisj(Seq(), phrase, fullyGeneralizes))
      case _ => generalizations
    }
    tq.copy(generalizations = Some(phraseOnlyGeneralizations))
  }
}

/** Query that has been 'tokenized' into a sequence of QExpr, the QExpr are then chunked together
  * into tokenSequence which can be marked as capture groups or not. Note this cannot represent
  * queries with nested capture groups.
  *
  * @param tokenSequences Chunked sequence of QExpr
  * @param generalizations Optionally, corresponding generalization for each QExpr
  */
case class TokenizedQuery(
    tokenSequences: Seq[QueryTokenSequence],
    generalizations: Option[Seq[Generalization]] = None
) {

  val size: Int = tokenSequences.map(_.queryTokens.size).sum
  require(generalizations.isEmpty || size == generalizations.get.size)

  def getOriginalQuery: QExpr = {
    TokenizedQuery.qexprFromSequence(tokenSequences.flatMap {
      case TokenSequence(seq) => seq
      case cts: CapturedTokenSequence => Seq(cts.getOriginalQuery)
    })
  }

  def getSeq: Seq[QExpr] = tokenSequences.flatMap(_.queryTokens)

  def getCaptureGroups: Seq[(String, Seq[QExpr])] = tokenSequences.flatMap {
    case TokenSequence(_) => None
    case CapturedTokenSequence(tokens, name, _) => Some((name, tokens))
  }

  /** @return the tokens of this, plus their Slot, and whether they are part
    * of a capture group or not
    */
  def getAnnotatedSeq: Seq[QuerySlotData] = {
    var onIndex = 0
    tokenSequences.zipWithIndex.flatMap {
      case (tokenSequence, sequenceIndex) =>
        val isCapture = tokenSequence.isInstanceOf[CapturedTokenSequence]
        tokenSequence.queryTokens.map { queryToken =>
          val gen = if (generalizations.isDefined) {
            Some(generalizations.get(onIndex))
          } else {
            None
          }
          val qsd = QuerySlotData(Some(queryToken), QueryToken(onIndex + 1), isCapture, gen)
          onIndex += 1
          qsd
        }
    }
  }

  /** @return the names each token in this.getSeq in order */
  def getNames: Seq[String] = {
    TokenizedQuery.getTokenNames(size)
  }

  /** @return each token in this paired with its name */
  def getNamedTokens: Seq[(String, QExpr)] = getNames.zip(getSeq)

  /** @return the sequence of QueryTokenSequence paired with the names of their tokens */
  def getSequencesWithNames: Seq[(QueryTokenSequence, Seq[String])] = {
    var remainingNames = getNames
    tokenSequences.map { tseq =>
      val (sequenceNames, rest) = remainingNames.splitAt(tseq.size)
      remainingNames = rest
      (tseq, sequenceNames)
    }
  }

  /** @return the generalize version of this query, assumes Generalization has been set */
  def getGeneralizeQuery: TokenizedQuery = {
    require(generalizations.isDefined)
    var remainingGeneralization = generalizations.get
    val newSequences = tokenSequences.map { tseq =>
      val (generalizations, rest) = remainingGeneralization.splitAt(tseq.size)
      remainingGeneralization = rest
      val newSeq = generalizations.zip(tseq.queryTokens).map {
        case (gen, qexpr) =>
          gen match {
            case GeneralizeToAny(min, max) => QRepetition(QWildcard(), min, max)
            case GeneralizeToNone() => qexpr
            case GeneralizeToDisj(pos, phrase, _) => QDisj((pos ++ phrase) :+ qexpr)
          }
      }
      tseq match {
        case cts: CapturedTokenSequence => cts.copy(queryTokens = newSeq)
        case ts: TokenSequence => ts.copy(queryTokens = newSeq)
      }
    }
    TokenizedQuery(newSequences, None)
  }
}
