package org.allenai.dictionary

import com.typesafe.config.Config
import nl.inl.blacklab.search.{ HitsWindow, Searcher, TextPattern }
import org.allenai.common.Config.EnhancedConfig
import org.allenai.common.Logging
import org.allenai.dictionary.ml.{ Suggestions, QuerySuggester }

import scala.util.{ Success, Try }

import java.util.concurrent.{ TimeoutException, TimeUnit, Executors, Callable }

case class SuggestQueryRequest(query: String, tables: Map[String, Table],
  target: String, narrow: Boolean, config: SuggestQueryConfig)
case class SuggestQueryConfig(beamSize: Int, depth: Int, maxSampleSize: Int, pWeight: Double,
  nWeight: Double, uWeight: Double, allowDisjunctions: Boolean)
case class ScoredStringQuery(query: String, score: Double, positiveScore: Double,
  negativeScore: Double, unlabelledScore: Double)
case class SuggestQueryResponse(
  original: ScoredStringQuery,
  suggestions: Seq[ScoredStringQuery],
  samplePercent: Double
)
case class WordInfoRequest(word: String, config: SearchConfig)
case class WordInfoResponse(word: String, posTags: Map[String, Int])
case class SearchConfig(limit: Int = 100, evidenceLimit: Int = 1)
case class SearchRequest(query: Either[String, QExpr], target: Option[String],
  tables: Map[String, Table], config: SearchConfig)
case class SearchResponse(qexpr: QExpr, groups: Seq[GroupedBlackLabResult])
case class CorpusDescription(name: String, description: Option[String])

case class SearchApp(config: Config) extends Logging {
  val name = config.getString("name")
  logger.debug(s"Building SearchApp for $name")
  val description = config.get[String]("description")
  val indexDir = DataFile.fromConfig(config)
  val searcher = Searcher.open(indexDir)
  def blackLabHits(textPattern: TextPattern, limit: Int): Try[HitsWindow] = Try {
    searcher.find(textPattern).window(0, limit)
  }
  def fromHits(hits: HitsWindow): Try[Seq[BlackLabResult]] = Try {
    BlackLabResult.fromHits(hits, name).toSeq
  }
  def semantics(query: QExpr): Try[TextPattern] = Try(BlackLabSemantics.blackLabQuery(query))
  def search(r: SearchRequest): Try[Seq[BlackLabResult]] = for {
    qexpr <- SearchApp.parse(r)
    interpolated <- QueryLanguage.interpolateTables(qexpr, r.tables)
    textPattern <- semantics(interpolated)
    hits <- blackLabHits(textPattern, r.config.limit)
    results <- fromHits(hits)
  } yield results
  def groupedSearch(req: SearchRequest): Try[SearchResponse] = for {
    results <- search(req)
    grouped = req.target match {
      case Some(target) => SearchResultGrouper.groupResults(req, results)
      case None => SearchResultGrouper.identityGroupResults(req, results)
    }
    qexpr <- SearchApp.parse(req)
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
    posTags = histogram.filterKeys(_._1 == "pos").map {
      case (a, b) => (a._2, b)
    }
    res = WordInfoResponse(req.word, posTags)
  } yield res
}

object SearchApp extends Logging {

  def parse(r: SearchRequest): Try[QExpr] = r.query match {
    case Left(queryString) => QueryLanguage.parse(queryString)
    case Right(qexpr) => Success(qexpr)
  }

  def suggestQuery(
    searchApps: Seq[SearchApp],
    request: SuggestQueryRequest,
    timeoutInSeconds: Long
  ): Try[SuggestQueryResponse] = for {
    query <- QueryLanguage.parse(request.query)
    suggestion <- Try {
      val callableSuggestsion = new Callable[Suggestions] {
        override def call(): Suggestions = {
          QuerySuggester.suggestQuery(searchApps.map(_.searcher), query,
            request.tables, request.target, request.narrow, request.config)
        }
      }
      val exService = Executors.newSingleThreadExecutor()
      val future = exService.submit(callableSuggestsion)
      try {
        future.get(timeoutInSeconds, TimeUnit.SECONDS)
      } catch {
        case to: TimeoutException =>
          logger.info(s"Suggestion for ${request.query} times out")
          future.cancel(true) // Interrupt the suggestions
          throw new TimeoutException("Query suggestion timed out")
      }
    }
    stringQueries <- Try(
      suggestion.suggestions.map(x => {
        ScoredStringQuery(QueryLanguage.getQueryString(x.query), x.score, x.positiveScore,
          x.negativeScore, x.unlabelledScore)
      })
    )
    original = suggestion.original
    originalString = ScoredStringQuery(QueryLanguage.getQueryString(original.query), original.score,
      original.positiveScore, original.negativeScore, original.unlabelledScore)
    totalDocs = searchApps.map(_.searcher.getIndexReader.numDocs()).sum.toDouble
    response = SuggestQueryResponse(originalString, stringQueries,
      suggestion.docsSampledFrom / totalDocs)
  } yield response
}
