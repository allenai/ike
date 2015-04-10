package org.allenai.dictionary

import akka.actor.{ Actor, ActorContext, ActorSystem, Props }
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.allenai.common.Logging
import spray.can.Http
import spray.http.{ CacheDirectives, HttpHeaders, StatusCodes }
import spray.httpx.SprayJsonSupport
import spray.routing.{ ExceptionHandler, HttpService }
import spray.util.LoggingContext

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal
import scala.xml.NodeSeq
import scala.language.postfixOps

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
  import JsonSerialization._
  import DictionaryToolWebapp.FutureWithGet

  logger.debug("Starting DictionaryToolActor") // this is just here to force logger initialization

  val config = ConfigFactory.load
  val searchApps = config.getConfigList("DictionaryToolWebapp.indices").asScala.map { config =>
    config.getString("name") -> Future { SearchApp(config) }
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
        }
      } ~ pathPrefix(name / "api" / "groupedSearch") {
        post {
          entity(as[SearchRequest]) { req =>
            complete(searcher.get.groupedSearch(req))
          }
        }
      } ~ pathPrefix(name / "api" / "wordInfo") {
        post {
          entity(as[WordInfoRequest]) { req =>
            complete(searcher.get.wordInfo(req))
          }
        }
      } ~ pathPrefix(name / "api" / "suggestQuery") {
        post {
          entity(as[SuggestQueryRequest]) { req =>
            complete(searcher.get.suggestQuery(req))
          }
        }
      }
  }.reduce(_ ~ _)

  val mainPageRoute = pathEndOrSingleSlash {
    get {
      complete {
        <html>
          <head>
            <title>OkCorpus</title>
            <meta charset="utf-8"/>
            <link href="/main.css" rel="stylesheet"/>
          </head>
          <body>
            <div align="center">
              <img src="/assets/logo.260x260.png" alt="OkCorpus"/>
              <br/>
              <div style="display: inline-block; font-size: 200%;" align="left">
                <p>Pick your corpus:</p>
                {
                  NodeSeq.fromSeq(searchApps.keys.toList.flatMap { name =>
                    NodeSeq.fromSeq(Seq(
                      <a href={ name }>{ name }</a>,
                      <br/>
                    ))
                  })
                }
              </div>
            </div>
          </body>
        </html>
      }
    }
  } ~ get {
    unmatchedPath { p => getFromFile(staticContentRoot + p) }
  }

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
  def receive: Actor.Receive = runRoute(mainPageRoute ~ serviceRoutes)
}
