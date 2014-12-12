package org.allenai.dictionary

import scala.util.parsing.combinator.RegexParsers

sealed trait QueryExpr {
  def tokens: Seq[Token] = this match {
    case t: Token => t :: Nil
    case cap: Capture => cap.expr.tokens
    case concat: Concat => concat.children.flatMap(_.tokens)
  }
}
sealed trait Token extends QueryExpr {
  def value: String
}
case class WordToken(value: String) extends Token
case class DictToken(value: String) extends Token
case class ClustToken(value: String) extends Token
case class Capture(expr: QueryExpr) extends QueryExpr
case class Concat(children: QueryExpr*) extends QueryExpr
case object Concat {
  def fromList(children: Seq[QueryExpr]): Concat = Concat(children:_*)
}

object QueryExprParser extends RegexParsers {
  def leftParen = "("
  def rightParen = ")"
  def wordToken: Parser[WordToken] = """[^$()\s]+""".r ^^ WordToken
  def dictToken: Parser[DictToken] = """\$[^$()\s]+""".r ^^ DictToken
  def clustToken: Parser[ClustToken] = """\^[01]+""".r ^^ ClustToken
  def token: Parser[Token] = dictToken | clustToken | wordToken
  def tokens: Parser[Concat] = rep1(token) ^^ Concat.fromList
  def capture: Parser[Capture] = leftParen ~> queryExpr <~ rightParen ^^ Capture
  def queryExpr: Parser[QueryExpr] = rep1(tokens | capture) ^^ Concat.fromList
  def parse(s: String): ParseResult[QueryExpr] = parseAll(queryExpr, s)
}

