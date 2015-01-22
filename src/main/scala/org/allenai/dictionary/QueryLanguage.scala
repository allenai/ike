package org.allenai.dictionary

import scala.util.parsing.input.Positional
import scala.util.parsing.combinator.RegexParsers
import java.util.regex.Pattern

sealed trait QExpr extends Positional
case class QWord(value: String) extends QExpr
case class QCluster(value: String) extends QExpr
case class QPos(value: String) extends QExpr
case class QDict(value: String) extends QExpr
object QWildcard extends QExpr
case class QNamed(qexpr: QExpr, name: String) extends QExpr
case class QUnnamed(qexpr: QExpr) extends QExpr
case class QNonCap(qexpr: QExpr) extends QExpr
case class QStar(qexpr: QExpr) extends QExpr
case class QPlus(qexpr: QExpr) extends QExpr
case class QSeq(qexprs: Seq[QExpr]) extends QExpr
case object QSeq {
  def fromSeq(seq: Seq[QExpr]): QExpr = seq match {
    case expr :: Nil => expr
    case _ => QSeq(seq)
  }
}
case class QDisj(qexprs: Seq[QExpr]) extends QExpr
case object QDisj {
  def fromSeq(seq: Seq[QExpr]): QExpr = seq match {
    case expr :: Nil => expr
    case _ => QDisj(seq)
  }
}

object QExprParser extends RegexParsers {
  val posTagSet = Seq("PRP$", "NNPS", "WRB", "WP$", "WDT", "VBZ", "VBP", "VBN", "VBG", "VBD", "SYM",
    "RBS", "RBR", "PRP", "POS", "PDT", "NNS", "NNP", "JJS", "JJR", "WP", "VB", "UH", "TO", "RP",
    "RB", "NN", "MD", "LS", "JJ", "IN", "FW", "EX", "DT", "CD", "CC")
  val posTagRegex = posTagSet.map(Pattern.quote).mkString("|").r
  // Turn off style---these are all just Parser[QExpr] definitions
  // scalastyle:off
  def word = positioned("""[^|\^$()\s*+]+""".r ^^ QWord)
  def cluster = positioned("""\^[01]+""".r ^^ { s => QCluster(s.tail) })
  def pos = positioned(posTagRegex ^^ QPos)
  def dict = positioned("""\$[^$()\s*+|]+""".r ^^ { s => QDict(s.tail) })
  def wildcard = positioned("\\.".r ^^^ QWildcard)
  def atom = positioned(wildcard | pos | dict | cluster | word)
  def captureName = "?<" ~> """[A-z0-9]+""".r <~ ">"
  def named = positioned("(" ~> captureName ~ expr <~ ")" ^^ { x => QNamed(x._2, x._1) })
  def unnamed = positioned("(" ~> expr <~ ")" ^^ QUnnamed)
  def nonCap = positioned("(?:" ~> expr <~ ")" ^^ QNonCap)
  def operand = positioned(named | nonCap | unnamed | atom)
  def starred = positioned(operand <~ "*" ^^ QStar)
  def plussed = positioned(operand <~ "+" ^^ QPlus)
  def modified = positioned(starred | plussed)
  def piece: Parser[QExpr] = positioned((modified | operand))
  def branch = positioned(rep1(piece) ^^ QSeq.fromSeq)
  def expr = positioned(repsep(branch, "|") ^^ QDisj.fromSeq)
  def parse(s: String) = parseAll(expr, s)
  // scalastyle:on
}
