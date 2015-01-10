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

case class QuerySeq(exprs: Seq[QueryExpr]) extends QueryExpr
case object QuerySeq {
  def fromSeq(seq: Seq[QueryExpr]): QueryExpr = seq match {
    case expr :: Nil => expr
    case _ => QuerySeq(seq)
  }
}

case class ContentRef(name: String) extends QueryExpr

case class QueryDisjunction(parts: Seq[QueryExpr]) extends QueryExpr

object QueryExprParser extends RegexParsers {
  
  val posTagSet = Seq("PRP$","NNPS","WRB","WP$","WDT","VBZ","VBP","VBN","VBG","VBD","SYM","RBS",
      "RBR","PRP","POS","PDT","NNS","NNP","JJS","JJR","WP","VB","UH","TO","RP","RB","NN","MD","LS",
      "JJ","IN","FW","EX","DT","CD","CC")
  val posTagRegex = posTagSet.map(Pattern.quote).mkString("|").r
  
  def leftParen = "("
  def rightParen = ")"
    
  def wildcard: Parser[QueryExpr] = "\\.".r ^^ { _ => WildCard }
  def content: Parser[Content] = """[^\^$()\s]+""".r ^^ Content
  def contentRef: Parser[ContentRef] = """\$[^$()\s]+""".r ^^ { s => ContentRef(s.tail) }
  def clusterPrefix: Parser[ClusterPrefix] = """\^[01]+\b""".r ^^ { s => ClusterPrefix(s.tail) }
  def posTag: Parser[PosTag] = posTagRegex ^^ PosTag
  
  def atom: Parser[QueryExpr] = wildcard | posTag | contentRef | clusterPrefix | content
  def atoms: Parser[QueryExpr] = rep1(atom) ^^ QuerySeq.fromSeq
  
  def capture: Parser[QueryCapture] = leftParen ~> queryExpr <~ rightParen ^^ QueryCapture
  
  def queryExpr: Parser[QueryExpr] = rep1(atoms | capture) ^^ QuerySeq.fromSeq
  
  def parse(s: String): ParseResult[QueryExpr] = parseAll(queryExpr, s)
}

object Foo extends App {
  import sext._
  val q = args.mkString(" ")
  println(QueryExprParser.parse(q).treeString)
}