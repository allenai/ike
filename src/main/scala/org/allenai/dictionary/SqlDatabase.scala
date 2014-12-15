package org.allenai.dictionary

import org.apache.commons.lang.StringEscapeUtils.escapeSql
import scalikejdbc._

case object SqlDatabase {
  
  val wordColumnName = "word"
  val clusterColumnName = "cluster"
  val freqColumnName = "freq"
  val tableName = "grams"
  val selectName = "result"
  val freqName = "totalFreq"
  val clusterLength = 20
  val concatOperator = " || ' ' || "
    
  def wordColumn(i: Int): String = s"$wordColumnName$i"
  
  def clusterColumn(i: Int): String = s"$clusterColumnName$i"
  
  def predicates(tokens: Seq[QToken]): Seq[SqlPredicate] = for {
    (t, i) <- tokens.zipWithIndex
    predicate = t match {
      case w: WordToken => Equals(wordColumn(i), Some(w.value))
      case c: ClustToken => Prefix(clusterColumn(i), c.value)
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
    (captureStart until captureStart + captureTokens.size) map wordColumn
  }
  
  def resultConcat(cs: Seq[String]): String = cs.mkString(concatOperator)
}

case class SqlDatabase(path: String, n: Int, batchSize: Int = 100000) {
  
  Class.forName("org.sqlite.JDBC")
  ConnectionPool.singleton(s"jdbc:sqlite:$path", null, null)
  implicit val session = AutoSession
  import SqlDatabase._
  
  val wordColumnNames = (0 until n) map wordColumn
  val clusterColumnNames = (0 until n) map clusterColumn
  val columnNames = wordColumnNames ++ clusterColumnNames :+ freqColumnName
  val numColumns = columnNames.size
  
  def create: Unit = {
    val wordSchema = wordColumnNames map { name => s"$name TEXT" } mkString(", ")
    val clusterSchema = clusterColumnNames map { n => s"$n VARCHAR($clusterLength)" } mkString(", ")
    val freqSchema = s"$freqColumnName INTEGER NOT NULL"
    val createTable = s"CREATE TABLE $tableName ($wordSchema, $clusterSchema, $freqSchema)"
    SQL(createTable).execute.apply
  }
  
  def createIndexes: Unit = columnNames foreach createColumnIndex
  
  def createColumnIndex(columnName: String): Unit =
    SQL(s"CREATE INDEX index$columnName ON $tableName ($columnName)").execute.apply
  
  def delete: Unit = SQL(s"DROP TABLE $tableName").execute.apply
  
  def gramRow(counted: Counted[NGram]): Seq[Any] = {
    val grams = counted.value.grams
    val words = grams.map(_.word).padTo(n, null)
    val clusts = grams.map(_.cluster).padTo(n, null)
    val cols = (words ++ clusts)
    cols :+ counted.count
  }
  
  def insert(grams: Iterable[Counted[NGram]]): Unit = insert(grams.iterator)
  
  def insert(grams: Iterator[Counted[NGram]]): Unit = {
    val cols = List.fill(numColumns)("?").mkString(", ")
    val query = s"INSERT INTO $tableName VALUES ($cols)"
    val rows = grams map gramRow
    for (rowBatch <- rows.grouped(batchSize)) {
      SQL(query).batch(rowBatch:_*).apply
    }
  }
  
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
    val cols = resultColumnNames(expr)
    SqlQuery(cols, predicates(tokens))
  }
  
  def queryString(q: SqlQuery): String = {
    val where = q.predicates.map(constraintString).mkString(" AND ")
    val resultColumn = resultConcat(q.resultCols)
    val selectResult = s"$resultColumn AS $selectName"
    val selectFreq = s"SUM($freqColumnName) AS $freqName"
    s"SELECT $selectResult, $selectFreq FROM $tableName WHERE $where GROUP BY $resultColumn"
  }
  
  def query(expr: QueryExpr): Iterable[QueryResult] = {
    val unpadded = sqlQuery(expr)
    val padding = for {
      i <- unpadded.predicates.size until n
      col = wordColumn(i)
    } yield Equals(col, None)
    val padded = unpadded.copy(predicates = unpadded.predicates ++ padding)
    select(padded)
  }

}

case class QueryResult(string: String, count: Int)
case class SqlQuery(resultCols: Seq[String], predicates: Seq[SqlPredicate])
sealed trait SqlPredicate
case class Equals(name: String, value: Option[String]) extends SqlPredicate
case class Prefix(name: String, value: String) extends SqlPredicate