package org.allenai.dictionary

import scala.util.parsing.combinator.RegexParsers
import org.allenai.common.immutable.Interval
import spray.json._
import DefaultJsonProtocol._
import java.util.regex.Pattern

sealed trait QueryExpr

case class Content(value: String) extends QueryExpr

case class ClusterPrefix(value: String) extends QueryExpr

case class PosTag(value: String) extends QueryExpr

object WildCard extends QueryExpr {
  override def toString: String = "WildCard"
}

case class QueryCapture(expr: QueryExpr) extends QueryExpr
case class QueryNonCapture(expr: QueryExpr) extends QueryExpr

case class QuerySeq(exprs: Seq[QueryExpr]) extends QueryExpr
case object QuerySeq {
  def fromSeq(seq: Seq[QueryExpr]): QueryExpr = seq match {
    case expr :: Nil => expr
    case _ => QuerySeq(seq)
  }
}

case class ContentRef(name: String) extends QueryExpr

case class QueryDisjunction(parts: Seq[QueryExpr]) extends QueryExpr
case object QueryDisjunction {
  def fromSeq(seq: Seq[QueryExpr]): QueryExpr = seq match {
    case expr :: Nil => expr
    case _ => QueryDisjunction(seq)
  }
}

case class QueryStar(expr: QueryExpr) extends QueryExpr
case class QueryPlus(expr: QueryExpr) extends QueryExpr

object QueryExprParser extends RegexParsers {
  val posTagSet = Seq("PRP$","NNPS","WRB","WP$","WDT","VBZ","VBP","VBN","VBG","VBD","SYM","RBS",
      "RBR","PRP","POS","PDT","NNS","NNP","JJS","JJR","WP","VB","UH","TO","RP","RB","NN","MD","LS",
      "JJ","IN","FW","EX","DT","CD","CC")
  val posTagRegex = posTagSet.map(Pattern.quote).mkString("|").r
  def leftParen = "("
  def nonCapLeftParen = "(?:"
  def rightParen = ")"
  def pipe = "|"
  def star = "*"
  def plus = "+"
  def wildcard = "\\.".r ^^^ WildCard
  def content = """[^|\^$()\s*+]+""".r ^^ Content
  def contentRef = """\$[^$()\s*+|]+""".r ^^ { s => ContentRef(s.tail) }
  def clusterPrefix = """\^[01]+""".r ^^ { s => ClusterPrefix(s.tail) }
  def posTag = posTagRegex ^^ PosTag
  def atom = wildcard | posTag | contentRef | clusterPrefix | content
  def capture = leftParen ~> expr <~ rightParen ^^ QueryCapture
  def nonCapture = nonCapLeftParen ~> expr <~ rightParen ^^ QueryNonCapture
  def operand = nonCapture | capture | atom
  def starred = operand <~ star ^^ QueryStar
  def plussed = operand <~ plus ^^ QueryPlus
  def modified = starred | plussed
  def piece: Parser[QueryExpr] = (modified | operand)
  def branch = rep1(piece) ^^ QuerySeq.fromSeq
  def expr = repsep(branch, pipe) ^^ QueryDisjunction.fromSeq
  def parse(s: String) = parseAll(expr, s)
}

object Foo extends App {
  import sext._
  val q = args.mkString(" ")
  println(QueryExprParser.parse(q).treeString)
}