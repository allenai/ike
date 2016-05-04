package org.allenai.dictionary

import org.allenai.common.Config._
import org.allenai.common.Logging
import org.allenai.dictionary.patterns.NamedPattern
import org.allenai.dictionary.persistence.Tablestore

import akka.actor.{ Actor, ActorContext, ActorSystem, Props }
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory }
import org.slf4j.LoggerFactory
import spray.can.Http
import spray.http.{ CacheDirectives, HttpHeaders, StatusCodes }
import spray.httpx.SprayJsonSupport
import spray.routing.{ ExceptionHandler, HttpService }
import spray.util.LoggingContext

import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps
import scala.util.control.NonFatal

object DictionaryToolWebapp {
  lazy val config = ConfigFactory.load().getConfig("DictionaryToolWebapp")
  val name = "dictionary-tool"
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("dictionary-tool")
    val port = config.getInt("port")
    val service = system.actorOf(Props[DictionaryToolActor], "webapp-actor")

    {
      implicit val timeout = Timeout(30.seconds)
      IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = port)
    }
  }

  implicit class FutureWithGet[T](val future: Future[T]) extends AnyVal {
    def get: T = Await.result(future, 0 nanos)
  }
}

class DictionaryToolActor extends Actor with HttpService with SprayJsonSupport with Logging {
  import org.allenai.dictionary.DictionaryToolWebapp.FutureWithGet
  import org.allenai.dictionary.JsonSerialization._

  import context.dispatcher
  import spray.json.DefaultJsonProtocol._

  val usageLogger = LoggerFactory.getLogger("Usage")

  logger.debug("Starting DictionaryToolActor") // this is just here to force logger initialization

  val config = ConfigFactory.load
  val searchApps = config.getConfigList("DictionaryToolWebapp.indices").asScala.map { config =>
    config.getString("name") -> Future { SearchApp(config) }
  }.toMap

  // Create instances of embedding based similar phrase searchers
  val word2vecPhrasesSearcher =
    new EmbeddingBasedPhrasesSearcher(ConfigFactory.load()[Config]("word2vecPhrasesSearcher"))
  val pmiEmbeddingPhrasesSearcher =
    new EmbeddingBasedPhrasesSearcher(ConfigFactory.load()[Config]("pmiPhrasesSearcher"))

  // Create a list of embedding based similar phrase searchers
  val searcherList = Seq(word2vecPhrasesSearcher, pmiEmbeddingPhrasesSearcher)
  // Create a combination searcher that combines similarity scores of all searchers
  val combinationPhraseSearcher = new EmbeddingSearcherCombinator(
    searcherList,
    ConfigFactory.load()[Config]("combinationPhraseSearcher")
  )

  // TODO: Once EmbeddingSearcherCombinator testing is done, pass on the combination
  // searcher object to SimilarPhrasesBasedTableExpander
  val tableExpander = new SimilarPhrasesBasedTableExpander(word2vecPhrasesSearcher)
  //val tableExpander = new SimilarPhrasesBasedTableExpander(combinationPhraseSearcher)

  implicit def myExceptionHandler(implicit log: LoggingContext): ExceptionHandler =
    ExceptionHandler {
      case NonFatal(e) =>
        requestUri { uri =>
          log.error(e, e.getMessage)
          complete(StatusCodes.InternalServerError -> e.getMessage)
        }
    }

  val serviceRoute = pathPrefix("api") {
    parameters('corpora.?) { corpora =>
      val corpusNames = corpora match {
        case None => searchApps.keys
        case Some(names) => names.split(' ').toSeq
      }
      val corpusNamesString = corpusNames.mkString(", ")
      val searchersFuture = Future.sequence(corpusNames.map(searchApps))

      path("groupedSearch") {
        post {
          entity(as[SearchRequest]) { req =>
            complete {
              usageLogger.info {
                val query = req.query match {
                  case Left(queryString: String) => queryString
                  case Right(_) => "with QExpr"
                }
                val user = req.userEmail.map(u => s" by $u").getOrElse("")
                s"groupedSearch $query on $corpusNamesString$user"
              }

              val query = SearchApp.parse(req).get

              val (tables, patterns) = req.userEmail match {
                case Some(userEmail) => (
                  Tablestore.tables(userEmail),
                  Tablestore.namedPatterns(userEmail)
                )
                case None => (Map.empty[String, Table], Map.empty[String, NamedPattern])
              }

              val interpolatedQuery = QueryLanguage.interpolateQuery(
                query,
                tables,
                patterns,
                word2vecPhrasesSearcher,
                tableExpander
              ).get
              val resultsFuture = searchersFuture.map { searchers =>
                val parResult = searchers.par.flatMap { searcher =>
                  searcher.search(interpolatedQuery, req.config).get
                }
                parResult.seq
              }
              val groupedFuture = for {
                results <- resultsFuture
              } yield {
                req.target match {
                  case Some(target) => SearchResultGrouper.groupResults(req, tables, results)
                  case None => SearchResultGrouper.identityGroupResults(req, results)
                }
              }
              val qexpr = SearchApp.parse(req).get
              groupedFuture.map { grouped => SearchResponse(qexpr, grouped) }
            }
          }
        }
      } ~
        path("wordInfo") {
          post {
            entity(as[WordInfoRequest]) { req =>
              complete(searchersFuture.map { searchers =>
                usageLogger.info(s"wordInfo for word ${req.word} on ($corpusNamesString)")

                val results = searchers.par.map(_.wordInfo(req).get)

                // find the word
                val word = results.head.word
                require(results.forall(_.word == word))

                // combine the pos tags
                def combineCountMaps[T](left: Map[T, Int], right: Map[T, Int]) =
                  left.foldLeft(right) {
                    case (map, newPair) =>
                      map.updated(newPair._1, map.getOrElse(newPair._1, 0) + newPair._2)
                  }
                val posTags = results.map(_.posTags).reduce(combineCountMaps[String])

                WordInfoResponse(word, posTags)
              })
            }
          }
        } ~
        path("suggestQuery") {
          post {
            entity(as[SuggestQueryRequest]) { req =>
              usageLogger.info(
                s"suggestQuery for query ${req.query} " +
                  s"on ($corpusNamesString)" +
                  s"by ${req.userEmail}"
              )
              val timeout = config.getConfig("QuerySuggester").
                getDuration("timeoutInSeconds", TimeUnit.SECONDS)
              complete(searchersFuture.map { searchers =>
                SearchApp.suggestQuery(searchers.toSeq, req, word2vecPhrasesSearcher, timeout)
              })
            }
          }
        }
    } ~ path("similarPhrases") {
      parameters('phrase) { phrase =>
        complete {
          usageLogger.info(s"similarPhrases for phrase $phrase")
          SimilarPhrasesResponse(word2vecPhrasesSearcher.getSimilarPhrases(phrase))
        }
      }
    }
  }

  val tablesRoute = pathPrefix("api" / "tables") {
    pathPrefix(Segment) { userEmail =>
      path(Segment) { tableName =>
        pathEnd {
          get {
            complete {
              Tablestore.tables(userEmail).get(tableName) match {
                case None => StatusCodes.NotFound
                case Some(table) if table.name == tableName => table
                case _ => StatusCodes.BadRequest
              }
            }
          } ~ put {
            entity(as[Table]) { table =>
              complete {
                usageLogger.info(s"tablePut for table $tableName by $userEmail")
                if (table.name == tableName) {
                  Tablestore.putTable(userEmail, table)
                } else {
                  StatusCodes.BadRequest
                }
              }
            }
          } ~ delete {
            complete {
              usageLogger.info(s"tableDelete for table $tableName by $userEmail")
              Tablestore.deleteTable(userEmail, tableName)
              StatusCodes.OK
            }
          }
        }
      } ~ pathEndOrSingleSlash {
        get {
          complete {
            Tablestore.tables(userEmail).values
          }
        }
      }
    }
  }

  val patternsRoute = pathPrefix("api" / "patterns") {
    pathPrefix(Segment) { userEmail =>
      path(Segment) { patternName =>
        pathEnd {
          get {
            complete {
              Tablestore.namedPatterns(userEmail).get(patternName) match {
                case None => StatusCodes.NotFound
                case Some(pattern) => pattern.pattern
              }
            }
          } ~ put {
            entity(as[String]) { pattern =>
              complete {
                usageLogger.info(s"patternPut for pattern $patternName by $userEmail")
                Tablestore.putNamedPattern(userEmail, NamedPattern(patternName, pattern))
              }
            }
          } ~ delete {
            complete {
              usageLogger.info(s"patternDelete for pattern $patternName by $userEmail")
              Tablestore.deleteNamedPattern(userEmail, patternName)
              StatusCodes.OK
            }
          }
        }
      } ~
        pathEndOrSingleSlash {
          get {
            complete(Tablestore.namedPatterns(userEmail).values)
          }
        }
    }
  }

  val corporaRoute = path("api" / "corpora") {
    pathEnd {
      complete {
        val readySearchApps = searchApps.filter(_._2.isCompleted)
        readySearchApps.toSeq.sortBy(_._1).map {
          case (corpusName, app) => CorpusDescription(corpusName, app.get.description)
        }
      }
    }
  }

  val mainPageRoute = pathEndOrSingleSlash {
    getFromFile("public/index.html")
  } ~ get {
    unmatchedPath { p => getFromFile("public" + p) }
  }

  def actorRefFactory: ActorContext = context
  val cacheControlMaxAge = HttpHeaders.`Cache-Control`(CacheDirectives.`max-age`(0))
  def receive: Actor.Receive = runRoute(
    mainPageRoute ~
      serviceRoute ~
      tablesRoute ~
      patternsRoute ~
      corporaRoute
  )
}
