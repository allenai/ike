package org.allenai.dictionary

import java.text.ParseException
import java.util.regex.Pattern

import scala.util.parsing.combinator.RegexParsers
import scala.util.{ Failure, Success, Try }

sealed trait QExpr
sealed trait QLeaf extends QExpr
sealed trait QAtom extends QExpr {
  val qexpr: QExpr
}
sealed trait QCapture extends QAtom

case class QWord(value: String) extends QLeaf
case class QCluster(value: String) extends QLeaf
case class QPos(value: String) extends QLeaf
case class QDict(value: String) extends QLeaf
case class QClusterFromWord(value: Int, wordValue: String, clusterId: String)
  extends QLeaf
case class QPosFromWord(value: Option[String], wordValue: String, posTags: Map[String, Int])
  extends QLeaf
case class QWildcard() extends QLeaf
case class QNamed(qexpr: QExpr, name: String) extends QCapture
case class QUnnamed(qexpr: QExpr) extends QCapture
case class QNonCap(qexpr: QExpr) extends QAtom
case class QSeq(qexprs: Seq[QExpr]) extends QExpr

sealed trait QRepeating extends QAtom {
  def min: Int
  def max: Int
}
case class QRepetition(qexpr: QExpr, min: Int, max: Int) extends QRepeating
case class QStar(qexpr: QExpr) extends QRepeating {
  def min = 0
  def max = -1
}
case class QPlus(qexpr: QExpr) extends QRepeating {
  def min = 1
  def max = -1
}
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
case class QAnd(qexpr1: QExpr, qexpr2: QExpr) extends QExpr

object QExprParser extends RegexParsers {
  val posTagSet = Seq("PRP$", "NNPS", "WRB", "WP$", "WDT", "VBZ", "VBP", "VBN", "VBG", "VBD", "SYM",
    "RBS", "RBR", "PRP", "POS", "PDT", "NNS", "NNP", "JJS", "JJR", "WP", "VB", "UH", "TO", "RP",
    "RB", "NN", "MD", "LS", "JJ", "IN", "FW", "EX", "DT", "CD", "CC")
  val posTagRegex = posTagSet.map(Pattern.quote).mkString("|").r
  // Turn off style---these are all just Parser[QExpr] definitions
  // scalastyle:off
  def wordRegex = """[^|\]\[\^$(){}\s*+,]+""".r
  def word = wordRegex ^^ QWord
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
  def integer = """-?[0-9]+""".r ^^ { _.toInt }
  def repetition = (operand <~ "[") ~ ((integer <~ ",") ~ (integer <~ "]")) ^^ { x =>
    QRepetition(x._1, x._2._1, x._2._2)
  }
  def starred = operand <~ "*" ^^ QStar
  def plussed = operand <~ "+" ^^ QPlus
  def modified = starred | plussed | repetition
  def piece: Parser[QExpr] = modified | operand
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
  def interpolateTables(expr: QExpr, tables: Map[String, Table]): Try[QExpr] = {
    def interp(value: String): QDisj = tables.get(value) match {
      case Some(table) if table.cols.size == 1 =>
        val rowExprs = for {
          row <- table.positive
          value <- row.values
          qseq = QSeq(value.qwords)
        } yield qseq
        QDisj(rowExprs)
      case Some(table) =>
        val name = table.name
        val ncol = table.cols.size
        throw new IllegalArgumentException(s"1-col table required: Table '$name' has $ncol columns")
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
      case QAnd(expr1, expr2) => QAnd(recurse(expr1), recurse(expr2))
      case QRepetition(expr, min, max) => QRepetition(recurse(expr), min, max)
    }
    Try(recurse(expr))
  }

  /** Converts a query to its string format
    *
    * @param query query to evaluate
    * @return String representation of the query
    * @throws NotImplementedError if the query contains QAnd, QPosFromWord, or QClusterFromWord
    */
  def getQueryString(query: QExpr): String = {

    def recurse(qexpr: QExpr): String = qexpr match {
      case QWord(value) => value
      case QCluster(value) => "^" + value
      case QPos(value) => value
      case QDict(value) => value
      case QWildcard() => "."
      case QSeq(children) => children.map(getQueryString).mkString(" ")
      case QDisj(children) => "{" + children.map(getQueryString).mkString(",") + "}"
      case QNamed(expr, name) => "(?<" + name + ">" + getQueryString(expr) + ")"
      case QUnnamed(expr) => "(" + getQueryString(expr) + ")"
      case QNonCap(expr) => "(?:" + getQueryString(expr) + ")"
      case QPlus(expr) => modifiableString(expr) + "+"
      case QStar(expr) => modifiableString(expr) + "*"
      case QRepetition(expr, min, max) => s"${modifiableString(expr)}[$min,$max]"
      case (QClusterFromWord(_, _, _) | QPosFromWord(_, _, _) | QAnd(_, _)) =>
        throw new NotImplementedError("No implementation for " + query.getClass.getName)
    }

    def modifiableString(qexpr: QExpr): String = qexpr match {
      case _: QLeaf | _: QCapture | _: QDisj => recurse(qexpr)
      case _ => "(?:" + recurse(qexpr) + ")"
    }

    recurse(query)
  }

  /** @param qexpr query expression to evaluate
    * @return All capture groups that are present in the query
    */
  def getCaptureGroups(qexpr: QExpr): Seq[QCapture] = qexpr match {
    case q: QCapture => Seq(q)
    case q: QAtom => getCaptureGroups(q.qexpr)
    case q: QLeaf => Seq()
    case QSeq(children) => children.flatMap(getCaptureGroups)
    case QDisj(children) => children.flatMap(getCaptureGroups)
    case QAnd(expr1, expr2) => getCaptureGroups(expr1) ++ getCaptureGroups(expr2)
  }

  /** @param qexpr query to evaluate
    * @return range of tokens the query will match, ends with -1 if the query
    *    can match a variable number of tokens'
    */
  def getQueryLength(qexpr: QExpr): (Int, Int) = qexpr match {
    case QDict(_) => (0, -1)
    case qr: QRepeating => {
      val (baseMin, baseMax) = getQueryLength(qr.qexpr)
      val max = if (baseMax == -1 || qr.max == -1) -1 else baseMax * qr.max
      (baseMin * qr.min, max)
    }
    case l: QLeaf => (1, 1)
    case QSeq(seq) =>
      val (mins, maxes) = seq.map(getQueryLength(_)).unzip
      val max = if (maxes.forall(_ != -1)) maxes.sum else -1
      (mins.sum, max)
    case QDisj(seq) =>
      val (mins, maxes) = seq.map(getQueryLength(_)).unzip
      val max = if (maxes.forall(_ != -1)) maxes.max else -1
      (mins.min, max)
    case QAnd(q1, q2) =>
      val (min1, max1) = getQueryLength(q1)
      val (min2, max2) = getQueryLength(q2)
      (Math.min(min1, min2), math.max(max1, max2))
    case q: QAtom => getQueryLength(q.qexpr)
  }

  /** Ensures that all capture groups in QExpr are named capture groups with names corresponding
    * to a column in tableCols. If QExpr contains unnamed capture groups they will be replaced with
    * named capture groups with names taken from tableCols in the order they appear
    *
    * @param qexpr Query expression to name capture groups within
    * @param tableCols Sequence of the columns in a table to be used to name unnamed capture
    *              groups
    * @throws IllegalArgumentException if QExpr contains a mix of named and unnamed capture groups,
    *                              if the name capture group do not have names corresponding
    *                              to the columns in tableCols, or if the query has the wrong
    *                              number of capture groups
    */
  def nameCaptureGroups(qexpr: QExpr, tableCols: Seq[String]): QExpr = {
    var unnamedCounts = 0
    def recurse(qexpr: QExpr): QExpr = qexpr match {
      case QNamed(q, name) =>
        require(tableCols contains name)
        QNamed(recurse(q), name)
      case QUnnamed(q) =>
        val name = tableCols(unnamedCounts)
        unnamedCounts += 1
        QNamed(recurse(q), name)
      case QStar(q) => QStar(recurse(q))
      case QPlus(q) => QPlus(recurse(q))
      case QNonCap(q) => QNonCap(recurse(q))
      case QSeq(children) => QSeq(children.map(recurse))
      case QDisj(children) => QDisj(children.map(recurse))
      case QAnd(expr1, expr2) => QAnd(recurse(expr1), recurse(expr2))
      case QRepetition(expr, min, max) => QRepetition(expr, min, max)
      case q: QLeaf => q
    }
    val output = recurse(qexpr)
    require(unnamedCounts == 0 || unnamedCounts == tableCols.size)
    output
  }
}
