package org.allenai.ike.ml.queryop

import org.allenai.ike._
import org.allenai.ike.ml.Token

object QLeafGenerator {

  /** @return True if word is a word that can be used in a string QExpr */
  def validWord(word: String): Boolean = {
    QueryLanguage.parser.wordRegex.pattern.matcher(word).matches()
  }

  /** @return True if pos is a POS tag that can be used in a string QExpr */
  def validPos(pos: String): Boolean = QExprParser.posTagSet contains pos

}

/** Given Tokens, builds QLeafs that would match that token
  *
  * @param pos whether to generate QPos
  * @param word whether to generate QWord
  * @param avoidSuggesting a specific QLeaf this should never suggest
  */
case class QLeafGenerator(pos: Boolean, word: Boolean,
    avoidSuggesting: Set[QLeaf] = Set()) {

  def generateLeaves(tokens: Seq[Token]): Iterable[QLeaf] = {
    if (tokens.isEmpty) {
      Seq()
    } else {
      val posOp = if (pos) {
        val head = tokens.head.pos
        if (tokens.tail.forall(_.pos == head) && QLeafGenerator.validPos(head)) {
          Some(QPos(head))
        } else {
          None
        }
      } else {
        None
      }
      val wordOp = if (word) {
        val head = tokens.head.word
        if (tokens.tail.forall(_.word == word) && QLeafGenerator.validWord(head)) {
          Some(QWord(head))
        } else {
          None
        }
      } else {
        None
      }
      (posOp ++ wordOp).filterNot(avoidSuggesting.contains(_))
    }
  }

  def generateLeaves(token: Token): Iterable[QLeaf] = {
    val posOp = if (pos && QLeafGenerator.validPos(token.pos)) {
      Some(QPos(token.pos))
    } else {
      None
    }

    val wordOp = if (word && QLeafGenerator.validWord(token.word)) {
      Some(QWord(token.word))
    } else {
      None
    }
    (posOp ++ wordOp).filterNot(avoidSuggesting.contains(_))
  }
}
