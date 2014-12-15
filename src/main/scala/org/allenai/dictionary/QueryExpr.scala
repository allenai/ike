package org.allenai.dictionary

import scala.util.parsing.combinator.RegexParsers

sealed trait QueryExpr
object QueryExpr {
  def tokens(expr: QueryExpr): Seq[QToken] = expr match {
    case t: QToken => t :: Nil
    case cap: Capture => tokens(cap.expr)
    case concat: Concat => concat.children.flatMap(tokens)
  }
  def captures(expr: QueryExpr): Seq[Capture] = expr match {
    case c: Capture => c +: captures(c.expr)
    case t: QToken => Nil
    case c: Concat => c.children.flatMap(captures)
  }
  def tokensBeforeCapture(expr: QueryExpr): Seq[QToken] = expr match {
    case cap: Capture => Nil
    case t: QToken => t :: Nil
    case cat: Concat => cat.children.map(tokensBeforeCapture).takeWhile(_.nonEmpty).flatten
  }
}

sealed trait QToken extends QueryExpr {
  def value: String
}
case class WordToken(value: String) extends QToken
case class DictToken(value: String) extends QToken
case class ClustToken(value: String) extends QToken
case class Capture(expr: QueryExpr) extends QueryExpr
case class Concat(children: QueryExpr*) extends QueryExpr
case object Concat {
  def fromSeq(children: Seq[QueryExpr]): QueryExpr = children match {
    case expr :: Nil => expr
    case seq => Concat(seq:_*)
  }
}

object QueryExprParser extends RegexParsers {
  def leftParen = "("
  def rightParen = ")"
  def wordToken: Parser[WordToken] = """[^$()\s]+""".r ^^ WordToken
  def dictToken: Parser[DictToken] = """\$[^$()\s]+""".r ^^ { s => DictToken(s.tail) } // strip $
  def clustToken: Parser[ClustToken] = """\^[01]""".r ^^ { s => ClustToken(s.tail) } //strip ^
  def token: Parser[QToken] = dictToken | clustToken | wordToken
  def tokens: Parser[QueryExpr] = rep1(token) ^^ Concat.fromSeq
  def capture: Parser[Capture] = leftParen ~> queryExpr <~ rightParen ^^ Capture
  def queryExpr: Parser[QueryExpr] = rep1(tokens | capture) ^^ Concat.fromSeq
  def parse(s: String): ParseResult[QueryExpr] = parseAll(queryExpr, s)
}

