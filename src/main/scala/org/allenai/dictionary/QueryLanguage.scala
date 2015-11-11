package org.allenai.dictionary

import org.allenai.dictionary.index.NlpAnnotate
import org.allenai.dictionary.patterns.NamedPattern

import org.apache.commons.lang.StringEscapeUtils

import java.text.ParseException
import java.util.regex.Pattern

import scala.util.control.NonFatal
import scala.util.parsing.combinator.RegexParsers
import scala.util.{ Failure, Success, Try }

sealed trait QExpr
sealed trait QLeaf extends QExpr
sealed trait QAtom extends QExpr {
  val qexpr: QExpr
}
sealed trait QCapture extends QAtom

case class QWord(value: String) extends QLeaf
case class QPos(value: String) extends QLeaf
case class QChunk(value: String) extends QLeaf
case class QDict(value: String) extends QLeaf
case class QNamedPattern(value: String) extends QLeaf
case class QPosFromWord(value: Option[String], wordValue: String, posTags: Map[String, Int])
  extends QLeaf
// Generalize a phrase to the nearest `pos` similar phrases
case class QGeneralizePhrase(qwords: Seq[QWord], pos: Int) extends QLeaf
case class SimilarPhrase(qwords: Seq[QWord], similarity: Double)
// A QGeneralizePhrase with its similar phrases pre-computed
case class QSimilarPhrases(qwords: Seq[QWord], pos: Int, phrases: Seq[SimilarPhrase])
    extends QLeaf {
  override def toString(): String = s"QSimilarPhrases(${qwords.map(_.value).mkString(" ")},$pos)"
}
// Generalize a table column to the nearest `pos` similar words/phrases
case class QGeneralizeTable(tableColumn: String, pos: Int) extends QLeaf
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
  def min: Int = 0
  def max: Int = -1
}
case class QPlus(qexpr: QExpr) extends QRepeating {
  def min: Int = 1
  def max: Int = -1
}
case object QSeq {
  def fromSeq(seq: Seq[QExpr]): QExpr = if (seq.lengthCompare(1) == 0) seq.head else QSeq(seq)
}
case class QDisj(qexprs: Seq[QExpr]) extends QExpr
case object QDisj {
  def fromSeq(seq: Seq[QExpr]): QExpr = if (seq.lengthCompare(1) == 0) seq.head else QDisj(seq)
}
case class QAnd(qexpr1: QExpr, qexpr2: QExpr) extends QExpr

object QExprParser extends RegexParsers {
  val posTagSet = Seq("PRP$", "NNPS", "WRB", "WP$", "WDT", "VBZ", "VBP", "VBN", "VBG", "VBD", "SYM",
    "RBS", "RBR", "PRP", "POS", "PDT", "NNS", "NNP", "JJS", "JJR", "WP", "VB", "UH", "TO", "RP",
    "RB", "NN", "MD", "LS", "JJ", "IN", "FW", "EX", "DT", "CD", "CC")
  val chunkTagSet = Seq("NP", "VP", "PP", "ADJP", "ADVP")
  val posTagRegex = posTagSet.map(Pattern.quote).mkString("|").r
  val chunkTagRegex = chunkTagSet.map(Pattern.quote).mkString("|").r
  // Turn off style---these are all just Parser[QExpr] definitions
  // scalastyle:off
  val integer = """-?[0-9]+""".r ^^ { _.toInt }

  // A word, with backslashes as the escape character.
  // Examples: foo, Qu\'ran, \,
  val wordRegex = """(?:\\.|[^|\]\[\^(){}\s*+,."~])+""".r

  // Uses the regex to find words, and tokenizes them before putting them into a QExpr
  val word = wordRegex ^^ { x =>
    val string = x.replaceAll("""\\(.)""", """$1""")
    NlpAnnotate.segment(string).flatMap(NlpAnnotate.tokenize).map(_.string).map(QWord)
  }
  val words = word ^^ QSeq.fromSeq

  // Example: improve~10
  val generalizedWord = (word <~ "~") ~ integer ^^ { x =>
    QGeneralizePhrase(x._1, x._2)
  }

  // Example: "better than"~10
  val generalizedPhrase = ("\"" ~> rep1(word) <~ "\"") ~ ("~" ~> integer).? ^^ { x =>
    QGeneralizePhrase(x._1.flatten, x._2.getOrElse(0))
  }
  val pos = posTagRegex ^^ QPos
  val chunk = chunkTagRegex ^^ QChunk

  // Example: $tablename
  val dictRegex = """\$[^$(){}\s*+|,]+""".r

  val dict = dictRegex ^^ { s => QDict(s.tail) }

  // This represents the same (dictRegex) but outputs just the query substring matching the regex
  // instead of constructing a QDict. Used to process table expansion queries (similarity queries)
  // with tilde.
  val dictString = dictRegex ^^ { s => s.tail }

  // Example: $tablename
  val generalizeTable = (dictString) ~ ("~" ~> integer) ^^ { x =>
    QGeneralizeTable(x._1, x._2)
  }

  // Example: #patternname
  val namedPattern = "#[a-zA-Z_]+".r ^^ { s => QNamedPattern(s.tail) }
  val wildcard = "\\.".r ^^^ QWildcard()
  val atom =
    wildcard | pos | chunk | generalizeTable | dict | namedPattern | generalizedWord |
      generalizedPhrase | words

  // Example: ?<capturegroup>
  val captureName = "?<" ~> """[A-z0-9]+""".r <~ ">"

  // Example: (?<capturegroup>.*), where the inner expression is .*
  val named = "(" ~> captureName ~ expr <~ ")" ^^ { x => QNamed(x._2, x._1) }
  val unnamed = "(" ~> expr <~ ")" ^^ QUnnamed
  val nonCap = "(?:" ~> expr <~ ")" ^^ QNonCap
  val curlyDisj = "{" ~> repsep(expr, ",") <~ "}" ^^ QDisj.fromSeq
  val operand = named | nonCap | unnamed | curlyDisj | atom

  // Prefix used to name capture groups that match table columns.
  // Specifically these are for capture groups that are tagged with a common integer tag
  // to specify that they are to be row-wise associated, i.e., their matching texts have to
  // appear in the same table row.
  val tableCaptureGroupPrefix = "Table Capture Group"

  // Example: foo[1,10], where foo is the expression that can be repeated from 1 to 10 times
  val repetition = (operand <~ "[") ~ ((integer <~ ",") ~ (integer <~ "]")) ^^ { x =>
    QRepetition(x._1, x._2._1, x._2._2)
  }
  val starred = operand <~ "*" ^^ QStar
  val plussed = operand <~ "+" ^^ QPlus
  val modified = starred | plussed | repetition
  val piece: Parser[QExpr] = modified | operand
  val branch = rep1(piece) ^^ QSeq.fromSeq
  def expr = repsep(branch, "|") ^^ QDisj.fromSeq
  def parse(s: String) = parseAll(expr, s)
  // scalastyle:on
}

// Use this so parser combinator objects are not in scope
object QueryLanguage {
  val parser = QExprParser
  def parse(
    s: String,
    allowCaptureGroups: Boolean = true
  ): Try[QExpr] = parser.parse(s) match {
    case parser.Success(result, _) =>
      Success(if (allowCaptureGroups) result else removeCaptureGroups(result))
    case parser.NoSuccess(message, next) =>
      val exception = new ParseException(message, next.pos.column)
      Failure(exception)
  }

  def removeCaptureGroups(expr: QExpr): QExpr = {
    expr match {
      case l: QLeaf => l
      case QSeq(children) => QSeq(children.map(removeCaptureGroups))
      case QDisj(children) => QDisj(children.map(removeCaptureGroups))
      case c: QCapture => QNonCap(c.qexpr)
      case QPlus(qexpr) => QPlus(removeCaptureGroups(qexpr))
      case QStar(qexpr) => QStar(removeCaptureGroups(qexpr))
      case QRepetition(qexpr, min, max) => QRepetition(removeCaptureGroups(qexpr), min, max)
      case QAnd(expr1, expr2) => QAnd(removeCaptureGroups(expr1), removeCaptureGroups(expr2))
      case QNonCap(qexpr) => QNonCap(removeCaptureGroups(qexpr))
    }
  }

  /** Tables can be referred to by their names. In the case of single-column tables,
    * `$table` is qualifying enough. In the case of multi-column tables, the required
    * column needs to be specified as `$table.column`. If results from matching different
    * columns in the same table need to be associated based on whether they appear in
    * the same row in the table, they need to be tagged by integer ids, like:
    * `$table.column1:0` and `$table.column2:0`.
    * If calling a similarity function to expand a table column, the query should look like:
    * `$table.column ~ 10` for 10 similar words/phrases to the entries already present in the
    * specified column. For a single column table, one can alternately just use the table name:
    * `$table ~ 10`.
    * @param expr the expression to be interpolated if there are references to tables.
    * @param tables tables currently loaded into OKCorpus.
    * @param patterns patterns  pre-loaded into OKCorpus.
    * @param tableExpanderOption the table similarity function to use to expand a table if requested.
    * @return attempted interpolated resulting expression.
    */
  def interpolateTables(
    expr: QExpr,
    tables: Map[String, Table],
    patterns: Map[String, NamedPattern],
    tableExpanderOption: Option[TableExpander]
  ): Try[QExpr] = {
    // Helper Method that splits a table query into its constituent parts: table name,
    // column name and tag name. Latter two are optional.
    // Takes a value of the form `table.col:0` or `table` or `table.col`.
    // Returns a 3-tuple with table name, (optional) column name and (optional) integer tag to
    // associate different columns with the same row.
    def getTableQueryParts(queryString: String): (String, Option[String], Option[Int]) = {
      val tableColTagRegex = """([^\.]+)\.([^:]+):(\d+)""".r
      val tableColRegex = """([^\.]+)\.(.+)""".r
      queryString match {
        case tableColTagRegex(table, col, tag) =>
          (table, Some(col), Some(tag.toInt))
        case tableColRegex(table, col) =>
          (table, Some(col), None)
        case _ =>
          (queryString, None, None)
      }
    }

    // Helper Method to get the table, the column name and index to process.
    // Throw an exception if column is not specified (None) and the table has more than one column.
    def getColumnIndexToProcess(
      tableName: String, columnNameOption: Option[String]
    ): (Table, String, Int) = {
      tables.get(tableName) match {
        case Some(table) =>
          columnNameOption match {
            case None =>
              // No column name was specified. This only makes sense if the specified
              // table has a single column.
              val numCols = table.cols.length
              if (numCols == 1) {
                (table, table.cols(0), 0)
              } else {
                throw new IllegalArgumentException(
                  s"Table '$tableName' has $numCols columns. Refine your query with a column name, "
                    + "using the format: `$tablename.columnname`."
                )
              }
            case Some(columnName) =>
              // Get index of the required column in the table.
              (table, columnName, table.getIndexOfColumn(columnName))
          }
        case None =>
          throw new IllegalArgumentException(s"Could not find table '$tableName'")
      }
    }

    // Gets a string of the form $table or $table.column or $table.column:0
    // and creates either a disjunctive expression with all possible matches for that
    // expression from the set of loaded tables or a named capture group containing a
    // disjunctive expression, if tags were specified in the query to associate results
    // matching different parts of the overall query.
    def expandDict(value: String): QExpr = {

      // Helper method to get data from specified column in specified table
      // and return a disjunction of the values from that column from all rows
      // of the table.
      def constructDisjunctiveQuery(table: Table, colIndex: Int): QDisj = {
        val rowExprs = for {
          row <- table.positive
        } yield {
          val value = row.values(colIndex)
          QSeq(value.qwords)
        }
        QDisj(rowExprs)
      }

      // For use in constructing a unique capture group name when a table-column-tag combination
      // is repeated in user input query.
      def uuid = java.util.UUID.randomUUID.toString

      // Get table name, column name and integer tag from query.
      val (tableName, columnNameOption, tagOption) = getTableQueryParts(value)

      // Process table query.
      val (table, columnName, columnIndex) = getColumnIndexToProcess(tableName, columnNameOption)

      // Form disjunction of all possible values in specified column in table.
      val qDisj = constructDisjunctiveQuery(table, columnIndex)

      // If this expression is tagged to be associated with other parts of
      // the query so that they come from the same table row, then enclose this in a
      // special "Table Capture Group" with appropriate name to use for post-processing
      // results. Otherwise simply return the disjunction.
      // We will name the special "Table Capture Group" as follows:
      // "Table Capture Group  <uniqueId> <tableName> <columnName> <tag>"
      // NOTE:
      // The uniqueId is a UUID. This is necessary because identical Table Capture
      // Groups can be repeated in an expression and because Capture Groups are
      // ultimately carried around as Maps with the name as key, we do not want any
      // group to be overwritten.
      (columnNameOption, tagOption) match {
        case (Some(columnName), Some(tag)) => QNamed(qDisj, s"${QExprParser.tableCaptureGroupPrefix}" +
          s" <$uuid> <${StringEscapeUtils.escapeXml(tableName)}>" +
          s" <${StringEscapeUtils.escapeXml(columnName)}> <$tag>")
        case _ => qDisj
      }
    }

    def expandNamedPattern(
      patternName: String,
      forbiddenPatternNames: Set[String] = Set.empty
    ): QExpr = {
      if (forbiddenPatternNames.contains(patternName)) {
        throw new IllegalArgumentException(s"Pattern $patternName recursively invokes itself.")
      }

      patterns.get(patternName) match {
        case Some(pattern) => try {
          recurse(parse(pattern.pattern, false).get, forbiddenPatternNames + patternName)
        } catch {
          case e if NonFatal(e) =>
            throw new IllegalArgumentException(
              s"While expanding pattern $patternName: ${e.getMessage}",
              e
            )
        }
        case None => throw new IllegalArgumentException(s"Could not find pattern '$patternName'")
      }
    }

    // Takes a string of the form $table or $table.column or $table.column and gets up to `pos`
    // phrases similar to the existing entries in the table (column).
    def generalizeTable(value: String, pos: Int): QExpr = {
      tableExpanderOption match {
        case Some(tableExpander) =>
          // Get table name, column name and integer tag from query. Tag does not make sense in
          // expansion query so it is expected to be None.
          val (tableName, columnNameOption, tagOption) = getTableQueryParts(value)

          // Error if integer tag is specified.
          if (tagOption.isDefined) {
            throw new IllegalArgumentException(
              s"Integer Tag not compatible with Table Expansion Query."
            )
          }

          val (table, columnName, columnIndex) = getColumnIndexToProcess(tableName, columnNameOption)
          QDisj(tableExpander.expandTableColumn(table, columnName).slice(0, pos - 1)
            map { x => QSeq(x.qwords) })

        case None =>
          throw new IllegalArgumentException("Table Expansion Request could not be completed. " +
            "No TableExpander specified!")
      }
    }

    def recurse(expr: QExpr, forbiddenPatternNames: Set[String] = Set.empty): QExpr = expr match {
      case QDict(value) => expandDict(value)
      case QGeneralizeTable(value, pos) => generalizeTable(value, pos)
      case QNamedPattern(value) => expandNamedPattern(value, forbiddenPatternNames)
      case l: QLeaf => l
      case QSeq(children) => QSeq(children.map(recurse(_, forbiddenPatternNames)))
      case QDisj(children) => QDisj(children.map(recurse(_, forbiddenPatternNames)))
      case QNamed(expr, name) => QNamed(recurse(expr, forbiddenPatternNames), name)
      case QNonCap(expr) => QNonCap(recurse(expr, forbiddenPatternNames))
      case QPlus(expr) => QPlus(recurse(expr, forbiddenPatternNames))
      case QStar(expr) => QStar(recurse(expr, forbiddenPatternNames))
      case QUnnamed(expr) => QUnnamed(recurse(expr, forbiddenPatternNames))
      case QAnd(expr1, expr2) =>
        QAnd(recurse(expr1, forbiddenPatternNames), recurse(expr2, forbiddenPatternNames))
      case QRepetition(expr, min, max) =>
        QRepetition(recurse(expr, forbiddenPatternNames), min, max)
    }
    Try(recurse(expr))
  }

  def interpolateSimilarPhrases(
    expr: QExpr,
    similarPhrasesSearcher: SimilarPhrasesSearcher
  ): Try[QExpr] = {
    def recurse(expr: QExpr): QExpr = expr match {
      case QGeneralizePhrase(phrase, pos) =>
        val similarPhrases =
          similarPhrasesSearcher.getSimilarPhrases(phrase.map(_.value).mkString(" "))
        QSimilarPhrases(phrase, pos, similarPhrases)
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

  /** Replaces QDict and QGeneralizePhrases expressions within a QExpr with
    * QDisj and QSimilarPhrase
    *
    * @param expr QExpr to interpolate
    * @param userEmail email of the user, used to find tables for dictionary expansions and named
    *                  patterns
    * @param similarPhrasesSearcher searcher to use when replacing QGeneralizePhrase expressions
    * @return the attempt to interpolated the query
    */
  def interpolateQuery(
    expr: QExpr,
    tables: Map[String, Table],
    patterns: Map[String, NamedPattern],
    similarPhrasesSearcher: SimilarPhrasesSearcher,
    tableExpander: TableExpander
  ): Try[QExpr] = {
    interpolateSimilarPhrases(
      interpolateTables(expr, tables, patterns, Some(tableExpander)).get,
      similarPhrasesSearcher
    )
  }

  /** Converts a query to its string format
    *
    * @param query query to evaluate
    * @return String representation of the query
    * @throws NotImplementedError if the query contains QAnd or QPosFromWord
    */
  def getQueryString(query: QExpr): String = {

    def recurse(qexpr: QExpr): String = qexpr match {
      case QWord(value) => value
      case QPos(value) => value
      case QChunk(value) => value
      case QDict(value) => "$" + value
      case QNamedPattern(value) => "#" + value
      case QWildcard() => "."
      case QSeq(children) => children.map(getQueryString).mkString(" ")
      case QDisj(children) => "{" + children.map(getQueryString).mkString(",") + "}"
      case QNamed(expr, name) => "(?<" + name + ">" + getQueryString(expr) + ")"
      case QUnnamed(expr) => "(" + getQueryString(expr) + ")"
      case QNonCap(expr) => "(?:" + getQueryString(expr) + ")"
      case QPlus(expr) => modifiableString(expr) + "+"
      case QStar(expr) => modifiableString(expr) + "*"
      case QRepetition(expr, min, max) => s"${modifiableString(expr)}[$min,$max]"
      case QGeneralizePhrase(phrase, pos) =>
        if (phrase.size == 1) {
          s"${recurse(phrase.head)}~$pos"
        } else {
          // Use triple quote syntax since scala's single quote interpolation has a bug with \"
          s""""${phrase.map(recurse).mkString(" ")}"~$pos"""
        }
      case QSimilarPhrases(phrase, pos, _) =>
        if (phrase.size == 1) {
          s"${recurse(phrase.head)}~$pos"
        } else {
          // Use triple quote syntax since scala's single quote interpolation has a bug with \"
          s""""${phrase.map(recurse).mkString(" ")}"~$pos"""
        }
      case _ => ???
    }

    def modifiableString(qexpr: QExpr): String = qexpr match {
      case _: QLeaf | _: QCapture | _: QDisj | _: QNonCap => recurse(qexpr)
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
    * can match a variable number of tokens'
    */
  def getQueryLength(qexpr: QExpr): (Int, Int) = qexpr match {
    case QDict(_) => (1, -1)
    case QNamedPattern(_) => (1, -1)
    case QGeneralizePhrase(_, _) => (1, -1)
    case QSimilarPhrases(qwords, pos, phrases) =>
      val lengths = qwords.size +: phrases.slice(0, pos).map(_.qwords.size)
      (lengths.min, lengths.max)
    case l: QLeaf => (1, 1)
    case qr: QRepeating => {
      val (baseMin, baseMax) = getQueryLength(qr.qexpr)
      val max = if (baseMax == -1 || qr.max == -1) -1 else baseMax * qr.max
      (baseMin * qr.min, max)
    }
    case QSeq(seq) =>
      val (mins, maxes) = seq.map(getQueryLength).unzip
      val max = if (maxes contains -1) -1 else maxes.sum
      (mins.sum, max)
    case QDisj(seq) =>
      val (mins, maxes) = seq.map(getQueryLength).unzip
      val max = if (maxes contains -1) -1 else maxes.max
      (mins.min, max)
    case QAnd(q1, q2) =>
      val (min1, max1) = getQueryLength(q1)
      val (min2, max2) = getQueryLength(q2)
      (Math.min(min1, min2), math.max(max1, max2))
    case q: QAtom => getQueryLength(q.qexpr)
  }

  /** Convert a QRepetition to a QStar or QPlus if possible, None if it can be deleted */
  def convertRepetition(qexpr: QRepetition): Option[QExpr] = qexpr match {
    case QRepetition(expr, 0, -1) => Some(QStar(expr))
    case QRepetition(expr, 1, -1) => Some(QPlus(expr))
    case QRepetition(expr, 1, 1) => Some(expr)
    case QRepetition(expr, 0, 0) => None
    case _ => Some(qexpr)
  }
}
