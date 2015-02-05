package org.allenai.dictionary

import scala.util.parsing.combinator.RegexParsers
import java.util.regex.Pattern
import scala.util.{ Try, Failure, Success }
import java.text.ParseException

sealed trait QExpr
case object QExpr {
  // TODO(tonyf): set up so recursive calls are simplified
  def children(qexpr: QExpr): Seq[QExpr] = qexpr match {
    case l: QLeaf => Nil
    case n: QNamed => n.qexpr :: Nil
    case u: QUnnamed => u.qexpr :: Nil
    case n: QNonCap => n.qexpr :: Nil
    case s: QStar => s.qexpr :: Nil
    case p: QPlus => p.qexpr :: Nil
    case s: QSeq => s.qexprs
    case d: QDisj => d.qexprs
  }
}

sealed trait QLeaf extends QExpr
case class QWord(value: String) extends QExpr with QLeaf
case class QCluster(value: String) extends QExpr with QLeaf
case class QPos(value: String) extends QExpr with QLeaf
case class QDict(value: String) extends QExpr with QLeaf
case class QClusterFromWord(value: String, wordValue: String, clusterId: String)
  extends QExpr
  with QLeaf
case class QWildcard() extends QExpr with QLeaf
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
  def word = """[^|\^$(){}\s*+,]+""".r ^^ QWord
  def cluster = """\^[01]+""".r ^^ { s => QCluster(s.tail) }
  def pos = posTagRegex ^^ QPos
  def dict = """\$[^$(){}\s*+|,]+""".r ^^ { s => QDict(s.tail) }
  def wildcard = "\\.".r ^^^ QWildcard()
  def atom = wildcard | pos | dict | cluster | word
  def captureName = "?<" ~> """[A-z0-9]+""".r <~ ">"
  def named = "(" ~> captureName ~ expr <~ ")" ^^ { x => QNamed(x._2, x._1) }
  def unnamed = "(" ~> expr <~ ")" ^^ QUnnamed
  def nonCap = "(?:" ~> expr <~ ")" ^^ QNonCap
  def curlyDisj = "{" ~> repsep(expr, ",") <~ "}" ^^ QDisj.fromSeq
  def operand = named | nonCap | unnamed | curlyDisj | atom
  def starred = operand <~ "*" ^^ QStar
  def plussed = operand <~ "+" ^^ QPlus
  def modified = starred | plussed
  def piece: Parser[QExpr] = (modified | operand)
  def branch = rep1(piece) ^^ QSeq.fromSeq
  def expr = repsep(branch, "|") ^^ QDisj.fromSeq
  def parse(s: String) = parseAll(expr, s)
  // scalastyle:on
}

// Use this so parser combinator objects are not in scope
object QueryLanguage {
  val parser = QExprParser
  def parse(s: String): Try[QExpr] = parser.parse(s) match {
    case parser.Success(result, _) => Success(result)
    case parser.NoSuccess(message, next) =>
      val exception = new ParseException(message, next.pos.column)
      Failure(exception)
  }
  def interpolateDictionaries(expr: QExpr, dicts: Map[String, Dictionary]): Try[QExpr] = {
    def interp(value: String): QDisj = dicts.get(value) match {
      case Some(dict) => Dictionary.positiveDisj(dict)
      case None =>
        throw new IllegalArgumentException(s"Could not find dictionary '$value'")
    }
    def recurse(expr: QExpr): QExpr = expr match {
      case QDict(value) => interp(value)
      case l: QLeaf => l
      case QSeq(children) => QSeq(children.map(recurse))
      case QDisj(children) => QDisj(children.map(recurse))
      case QNamed(expr, name) => QNamed(recurse(expr), name)
      case QNonCap(expr) => QNonCap(recurse(expr))
      case QPlus(expr) => QPlus(recurse(expr))
      case QStar(expr) => QStar(recurse(expr))
      case QUnnamed(expr) => QUnnamed(recurse(expr))
    }
    Try(recurse(expr))
  }
}