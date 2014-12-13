package org.allenai.dictionary

import org.apache.commons.lang.StringEscapeUtils.escapeSql
import scalikejdbc._

case class DatabaseOperations(path: String, n: Int, batchSize: Int = 100000) {
  
  Class.forName("org.sqlite.JDBC")
  ConnectionPool.singleton(s"jdbc:sqlite:$path", null, null)
  implicit val session = AutoSession
  val numCols = n * 2 + 1
  
  def create: Unit = {
    val tableName = Database.tableName
    val wCols = (0 until n) map Database.wordColumn
    val wColNames = wCols map { name => s"$name TEXT" } mkString(", ")
    val cCols = (0 until n) map Database.clusterColumn
    val cColNames = cCols map { name => s"$name VARCHAR(20)" } mkString(", ")
    val createTable = s"CREATE TABLE $tableName ($wColNames, $cColNames, COUNT INTEGER NOT NULL)"
    SQL(createTable).execute.apply
  }
  
  def delete: Unit = SQL(s"DROP TABLE ${Database.tableName}").execute.apply
  
  def gramRow(gram: CountedNGram): Seq[Any] = {
    val words = gram.tokens.map(_.word)
    val clusts = gram.tokens.map(_.cluster)
    val cols = (words ++ clusts).padTo(2 * n, null)
    cols :+ gram.count
  }
  
  def insert(grams: Iterable[CountedNGram]): Unit = insert(grams.iterator)
  
  def insert(grams: Iterator[CountedNGram]): Unit = {
    import Database.tableName
    val cols = List.fill(numCols)("?").mkString(", ")
    val query = s"INSERT INTO $tableName VALUES ($cols)"
    val rows = grams map gramRow
    for (rowBatch <- rows.grouped(batchSize)) {
      SQL(query).batch(rowBatch:_*).apply
    }
  }
  
  def select(query: DatabaseQuery): Iterable[QueryResult] = {
    val queryString = Database.queryString(query)
    val results = SQL(queryString) map {
      result => QueryResult(result.string(Database.selectName), 0)
    }
    results.list.apply
  }

}

case class WordWithCluster(word: String, cluster: String)

case class CountedNGram(tokens: Seq[WordWithCluster], count: Int)

case class QueryResult(string: String, count: Int)

object Database {
  
  def wordColumnName = "word"
  def clusterColumnName = "cluster"
  def tableName = "grams"
  def selectName = "result"
    
  def wordColumn(i: Int): String = s"$wordColumnName$i"
  def clusterColumn(i: Int): String = s"$clusterColumnName$i"
  
  def constraints(tokens: Seq[Token]): Seq[QueryConstraint] = for {
    (t, i) <- tokens.zipWithIndex
    constraint = t match {
      case w: WordToken => Equals(wordColumn(i), w.value)
      case c: ClustToken => Prefix(clusterColumn(i), c.value)
      case d: DictToken =>
        throw new IllegalArgumentException(s"Cannot convert $d to constraint")
    }
  } yield constraint
  
  def captures(expr: QueryExpr): Seq[Capture] = expr match {
    case c: Capture => c +: captures(c.expr)
    case t: Token => Nil
    case c: Concat => c.children.flatMap(captures)
  }
  
  def tokensBeforeCapture(expr: QueryExpr): Seq[Token] = expr match {
    case cap: Capture => Nil
    case t: Token => t :: Nil
    case cat: Concat => cat.children.map(tokensBeforeCapture).takeWhile(_.nonEmpty).flatten
  }
  
  def select(expr: QueryExpr): QuerySelect = {
    val captureTokens = captures(expr) match {
      case head :: Nil => head.tokens
      case other =>
        throw new IllegalArgumentException(s"Expected 1 capture group; found ${other.size}")
    }
    val captureStart = tokensBeforeCapture(expr).size
    val selectCols = (captureStart until captureStart + captureTokens.size) map wordColumn
    QuerySelect(selectCols)
  }
  
  def constraintString(c: QueryConstraint): String = c match {
    case Equals(n, v) => s"$n = '${escapeSql(v)}'"
    case Prefix(n, v) => s"$n LIKE '${escapeSql(v)}%'"
  }
  
  def selectString(s: QuerySelect): String = s.colNames.mkString(" || ' ' || ")
  
  def queryString(q: DatabaseQuery): String = {
    val where = q.constraints.map(constraintString).mkString(" AND ")
    val select = selectString(q.select)
    s"""SELECT $select AS $selectName FROM $tableName WHERE $where"""
  }
  
  def query(expr: QueryExpr): DatabaseQuery = DatabaseQuery(select(expr), constraints(expr.tokens))
  
}

case class DatabaseQuery(select: QuerySelect, constraints: Seq[QueryConstraint])
case class QuerySelect(colNames: Seq[String])
sealed trait QueryConstraint
case class Equals(name: String, value: String) extends QueryConstraint
case class Prefix(name: String, value: String) extends QueryConstraint