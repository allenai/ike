package org.allenai.dictionary

import scala.util.parsing.combinator.RegexParsers
import org.allenai.common.immutable.Interval

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
  def evalDicts(expr: QueryExpr, dicts: Map[String, Seq[QueryExpr]]): Seq[QueryExpr] = expr match {
    case DictToken(name) => dicts.get(name) match {
      case Some(otherExprs) => otherExprs
      case None => throw new IllegalArgumentException(s"Cannot resolve dictionary '$name'")
    }
    case t: QToken => t :: Nil
    case cap: Capture => for (replacement <- evalDicts(cap.expr, dicts)) yield Capture(replacement)
    case c: Concat => {
      val expandedChildren = c.children.map(evalDicts(_, dicts))
      for {
        product <- cartesian(expandedChildren)
        newExpr = Concat(product:_*)
      } yield newExpr
    }
  }
  def cartesian[A](xs: Traversable[Traversable[A]]): Seq[Seq[A]] = xs.foldLeft(Seq(Seq.empty[A])) {
    (x, y) => for (a <- x; b <- y) yield a :+ b 
  }
  def tokenOffsets(input: String, tokens: Seq[QToken]): Seq[Interval] =
    tokenOffsets(input, tokens, 0)
  def tokenOffsets(input: String, tokens: Seq[QToken], start: Int): Seq[Interval] = tokens match {
    case Nil => Nil
    case head :: rest => {
      val value = head.value
      val tstart = input.indexOf(value, start)
      assert(tstart >= 0, s"Could not find offsets for $head")
      val tend = tstart + value.size
      val interval = Interval.open(tstart, tend)
      interval +: tokenOffsets(input, rest, tend)
    }
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
  def clustToken: Parser[ClustToken] = """\^[01]+""".r ^^ { s => ClustToken(s.tail) } //strip ^
  def token: Parser[QToken] = dictToken | clustToken | wordToken
  def tokens: Parser[QueryExpr] = rep1(token) ^^ Concat.fromSeq
  def capture: Parser[Capture] = leftParen ~> queryExpr <~ rightParen ^^ Capture
  def queryExpr: Parser[QueryExpr] = rep1(tokens | capture) ^^ Concat.fromSeq
  def parse(s: String): ParseResult[QueryExpr] = parseAll(queryExpr, s)
  def tokenize(s: String): Seq[QToken] = QueryExpr.tokens(parse(s).get)
}

