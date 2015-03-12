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

case class WordInfoRequest(word: String, config: SearchConfig)
case class WordInfoResponse(word: String, clusterId: Option[String], posTags: Map[String, Int])
case class SearchConfig(limit: Int = 100, evidenceLimit: Int = 1)
case class SearchRequest(query: Either[String, QExpr], target: Option[String],
  tables: Map[String, Table], config: SearchConfig)
case class SearchResponse(qexpr: QExpr, groups: Seq[GroupedBlackLabResult])

case class SearchApp(config: Config) {
  val indexDir = DataFile.fromConfig(config)
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
    interpolated <- QueryLanguage.interpolateTables(qexpr, r.tables)
    textPattern <- semantics(interpolated)
    hits <- blackLabHits(textPattern, r.config.limit)
    results <- fromHits(hits)
  } yield results
  def groupedSearch(req: SearchRequest): Try[SearchResponse] = for {
    results <- search(req)
    grouped = SearchResultGrouper.groupResults(req, results)
    qexpr <- parse(req)
    resp = SearchResponse(qexpr, grouped)
  } yield resp
  def wordAttributes(req: WordInfoRequest): Try[Seq[(String, String)]] = for {
    textPattern <- semantics(QWord(req.word))
    hits <- blackLabHits(textPattern, req.config.limit)
    results <- fromHits(hits)
    data = results.flatMap(_.matchData)
    attrs = data.flatMap(_.attributes.toSeq)
  } yield attrs
  def attrHist(attrs: Seq[(String, String)]): Map[(String, String), Int] =
    attrs.groupBy(identity).mapValues(_.size)
  def attrModes(attrs: Seq[(String, String)]): Map[String, String] = {
    val histogram = attrHist(attrs)
    val attrKeys = attrs.map(_._1).distinct
    val results = for {
      key <- attrKeys
      subHistogram = histogram.filterKeys(_._1 == key)
      if subHistogram.size > 0
      attrMode = subHistogram.keys.maxBy(subHistogram)
    } yield attrMode
    results.toMap
  }
  def wordInfo(req: WordInfoRequest): Try[WordInfoResponse] = for {
    attrs <- wordAttributes(req)
    histogram = attrHist(attrs)
    modes = attrModes(attrs)
    clusterId = modes.get("cluster")
    posTags = histogram.filterKeys(_._1 == "pos").map {
      case (a, b) => (a._2, b)
    }
    res = WordInfoResponse(req.word, clusterId, posTags)
  } yield res
}
