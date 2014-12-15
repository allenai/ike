package org.allenai.dictionary

import org.apache.commons.lang.StringEscapeUtils.escapeSql
import scalikejdbc._

case class NGramTable(n: Int) {
  import NGramTable._
  val name = s"$tablePrefix$n"
  val wordColumnNames = (0 until n) map wordColumnName
  val clusterColumnNames = (0 until n) map clusterColumnName
  val columnNames = wordColumnNames ++ clusterColumnNames :+ freqColumnName
  val numColumns = columnNames.size
  
  val createStatement: String = {
    val wordSchema = wordColumnNames map { name => s"$name TEXT NOT NULL" } mkString(", ")
    val clusterSchema = clusterColumnNames map { 
      name => s"$name VARCHAR($clusterLength) NOT NULL" } mkString(", ")
    val freqSchema = s"$freqColumnName INTEGER NOT NULL"
    s"CREATE TABLE $name ($wordSchema, $clusterSchema, $freqSchema)"
  }

  val indexStatements: Seq[String] = columnNames map { columnName => 
    s"CREATE INDEX index_${columnName}_$name ON $name ($columnName)" }
  
  val insertStatement = {
    val places = List.fill(numColumns)("?").mkString(", ")
    s"INSERT INTO $name VALUES ($places)"
  }
  
  def gramRow(counted: Counted[NGram]): Seq[Any] = {
    val grams = counted.value.grams
    val count = counted.count
    assert(grams.size == n, s"Cannot insert $grams into $name")
    val words = grams.map(_.word)
    val clusts = grams.map(_.cluster)
    words ++ clusts :+ count
  }

}

case object NGramTable {
  val wordColumnName = "word"
  val clusterColumnName = "cluster"
  val freqColumnName = "freq"
  val tablePrefix = "grams"
  val clusterLength = 20
  def wordColumnName(i: Int): String = s"$wordColumnName$i"
  def clusterColumnName(i: Int): String = s"$clusterColumnName$i"
}

case object SqlDatabase {
  
  val selectName = "result"
  val freqName = "totalFreq"
  val concatOperator = " || ' ' || "
  
  def predicates(tokens: Seq[QToken]): Seq[SqlPredicate] = for {
    (t, i) <- tokens.zipWithIndex
    predicate = t match {
      case w: WordToken => Equals(NGramTable.wordColumnName(i), Some(w.value))
      case c: ClustToken => Prefix(NGramTable.clusterColumnName(i), c.value)
      case d: DictToken =>
        throw new IllegalArgumentException(s"Cannot convert dictionary $d to predicate")
    }
  } yield predicate
  
  def resultColumnNames(expr: QueryExpr): Seq[String] = {
    val captureTokens = QueryExpr.captures(expr).toList match {
      case head :: Nil => QueryExpr.tokens(head)
      case other =>
        throw new IllegalArgumentException(s"Expected 1 capture group; found ${other.size}")
    }
    val captureStart = QueryExpr.tokensBeforeCapture(expr).size
    (captureStart until captureStart + captureTokens.size) map NGramTable.wordColumnName
  }
  
  def resultConcat(cs: Seq[String]): String = cs.mkString(concatOperator)

}

case class SqlDatabase(path: String, n: Int, batchSize: Int = 100000) {
  
  Class.forName("org.sqlite.JDBC")
  ConnectionPool.singleton(s"jdbc:sqlite:$path", null, null)
  implicit val session = AutoSession
  import SqlDatabase._

  val tables = (1 to n).map(NGramTable(_))
  
  def create: Unit = tables foreach { t => SQL(t.createStatement).execute.apply }
  
  def createIndexes: Unit = tables.flatMap(_.indexStatements).map(SQL(_).execute.apply)
  
  def delete: Unit = tables foreach { t => SQL(s"DROP TABLE ${t.name}").execute.apply }
  
  def insert(grams: Iterator[Counted[NGram]]): Unit = for {
    counted <- grams
    size = counted.value.grams.size
    table = tables(size - 1)
    row = table.gramRow(counted)
    statement = SQL(table.insertStatement)
  } statement.bind(row:_*).update.apply
  
  def select(query: SqlQuery): Iterable[QueryResult] = {
    val q = queryString(query)
    val results = SQL(q) map {
      result => QueryResult(result.string(selectName), result.int(freqName))
    }
    results.list.apply
  }
  
  def constraintString(p: SqlPredicate): String = p match {
    case Equals(n, Some(v)) => s"$n = '${escapeSql(v)}'"
    case Equals(n, None) => s"$n IS NULL"
    case Prefix(n, v) => s"$n LIKE '${escapeSql(v)}%'"
  }
  
  def sqlQuery(expr: QueryExpr): SqlQuery = {
    val tokens = QueryExpr.tokens(expr)
    assert(tokens.size <= n, s"Cannot query $expr on table size $n")
    val table = tables(tokens.size - 1)
    val cols = resultColumnNames(expr)
    SqlQuery(table.name, cols, predicates(tokens))
  }
  
  def queryString(q: SqlQuery): String = {
    val tableName = q.table
    val freqColumnName = NGramTable.freqColumnName
    val where = q.predicates.map(constraintString).mkString(" AND ")
    val resultColumn = resultConcat(q.resultCols)
    val selectResult = s"$resultColumn AS $selectName"
    val selectFreq = s"SUM($freqColumnName) AS $freqName"
    s"SELECT $selectResult, $selectFreq FROM $tableName WHERE $where GROUP BY $resultColumn"
  }
  
  def query(expr: QueryExpr): Iterable[QueryResult] = select(sqlQuery(expr))

}

case class QueryResult(string: String, count: Int)
case class SqlQuery(table: String, resultCols: Seq[String], predicates: Seq[SqlPredicate])
sealed trait SqlPredicate
case class Equals(name: String, value: Option[String]) extends SqlPredicate
case class Prefix(name: String, value: String) extends SqlPredicate