package org.allenai.dictionary

import com.typesafe.config.ConfigFactory
import java.io.File
import nl.inl.blacklab.search.Searcher
import com.typesafe.config.Config
import org.allenai.common.immutable.Interval
import nl.inl.blacklab.search.TextPattern
import scala.util.Try
import nl.inl.blacklab.search.HitsWindow
import scala.util.Failure
import scala.util.Success

case class ParseRequest(query: String)

case class SearchConfig(limit: Int = 100, evidenceLimit: Int = 1, groupBy: Option[String] = None)

case class SearchRequest(query: Either[String, QExpr], dictionaries: Map[String, Dictionary], config: SearchConfig)

case class SearchResponse(qexpr: QExpr, rows: Seq[GroupedBlackLabResult])

case class SearchApp(config: Config) {
  val indexDir = config.getString("location") match {
    case "file" => new File(config.getString("path"))
    case "datastore" =>
      val ref = DatastoreRef.fromConfig(config.getConfig("item"))
      ref.directoryPath.toFile
    case _ => throw new IllegalArgumentException(s"'location' must be either 'file' or 'datastore")
  }
  val searcher = Searcher.open(indexDir)
  def blackLabHits(textPattern: TextPattern, limit: Int): Try[HitsWindow] = Try {
    searcher.find(textPattern).window(0, limit)
  }
  def fromHits(hits: HitsWindow): Try[Seq[BlackLabResult]] = Try {
    BlackLabResult.fromHits(hits).toSeq
  }
  def semantics(query: QExpr): Try[TextPattern] = Try(BlackLabSemantics.blackLabQuery(query))
  def parse(r: SearchRequest): Try[QExpr] = r.query match {
    case Left(queryString) => QueryLanguage.parse(queryString)
    case Right(qexpr) => Success(qexpr)
  }
  def search(r: SearchRequest): Try[Seq[BlackLabResult]] = for {
    qexpr <- parse(r)
    interpolated <- QueryLanguage.interpolateDictionaries(qexpr, r.dictionaries)
    textPattern <- semantics(interpolated)
    hits <- blackLabHits(textPattern, r.config.limit)
    results <- fromHits(hits)
  } yield results
  def groupedSearch(req: SearchRequest): Try[SearchResponse] = for {
    results <- search(req)
    keyed = results map (keyResult(req, _))
    grouped = keyed groupBy keyString
    groupedLimit = grouped mapValues (_.take(req.config.evidenceLimit))
    mapped = grouped map {
      case (key, results) =>
        val count = results.size
        val topResults = results.take(req.config.evidenceLimit)
        GroupedBlackLabResult(key, count, topResults)
    }
    qexpr <- parse(req)
    resp = SearchResponse(qexpr, mapped.toSeq)
  } yield resp
  def keyResult(req: SearchRequest, result: BlackLabResult): KeyedBlackLabResult = {
    val providedInterval = for {
      name <- req.config.groupBy
      i <- result.captureGroups.get(name)
    } yield i
    val firstInterval = result.captureGroups.values.toSeq.headOption
    val candidateIntervals = Seq(providedInterval, firstInterval, Some(result.matchOffset))
    val keyInterval = candidateIntervals.flatten.head
    KeyedBlackLabResult(keyInterval, result)
  }
  def keyString(kr: KeyedBlackLabResult): String = {
    val i = kr.key
    val wordData = kr.result.wordData.slice(i.start, i.end)
    val words = wordData map (_.word.toLowerCase.trim)
    words mkString " "
  }
}
