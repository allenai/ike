package org.allenai.dictionary

import org.allenai.common.Logging
import spray.routing.HttpService
import spray.httpx.SprayJsonSupport
import akka.actor.Actor
import spray.util.LoggingContext
import spray.routing.ExceptionHandler
import spray.http.StatusCodes
import spray.http.HttpHeaders
import spray.http.CacheDirectives
import akka.actor.ActorSystem
import akka.actor.Props
import akka.util.Timeout
import scala.concurrent.duration.DurationInt
import akka.io.IO
import spray.can.Http
import akka.pattern.ask
import akka.actor.ActorContext
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConverters._

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
}

class DictionaryToolActor extends Actor with HttpService with SprayJsonSupport with Logging {
  import JsonSerialization._

  logger.debug("Starting DictionaryToolActor") // this is just here to force logger initialization

  val config = ConfigFactory.load
  val searchApps = config.getConfigList("DictionaryToolWebapp.indices").asScala.par.map { config =>
    config.getString("name") -> SearchApp(config)
  }.toMap

  val staticContentRoot = "public"
  val serviceRoutes = searchApps.map {
    case (name, searcher) =>
      pathPrefix(name) {
        pathSingleSlash {
          get {
            getFromFile(staticContentRoot + "/index.html")
          }
        } ~ pathEnd {
          redirect(s"/$name/", StatusCodes.PermanentRedirect)
        } ~ get {
          unmatchedPath { p => getFromFile(staticContentRoot + p) }
        }
      } ~ pathPrefix(name / "api" / "groupedSearch") {
        post {
          entity(as[SearchRequest]) { req =>
            complete(searcher.groupedSearch(req))
          }
        }
      } ~ pathPrefix(name / "api" / "wordInfo") {
        post {
          entity(as[WordInfoRequest]) { req =>
            complete(searcher.wordInfo(req))
          }
        }
      } ~ pathPrefix(name / "api" / "suggestQuery") {
        post {
          entity(as[SuggestQueryRequest]) { req =>
            complete(searcher.suggestQuery(req))
          }
        }
      }
  }.reduce(_ ~ _)

  implicit def myExceptionHandler(implicit log: LoggingContext): ExceptionHandler =
    ExceptionHandler {
      case NonFatal(e) =>
        requestUri { uri =>
          log.error(toString, e)
          complete(StatusCodes.InternalServerError -> e.getMessage)
        }
    }
  def actorRefFactory: ActorContext = context
  val cacheControlMaxAge = HttpHeaders.`Cache-Control`(CacheDirectives.`max-age`(0))
  def receive: Actor.Receive =
    runRoute(logRequest("FOO") { serviceRoutes })
}
