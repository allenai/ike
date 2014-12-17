package org.allenai.dictionary

import spray.routing.HttpService
import spray.httpx.SprayJsonSupport
import spray.json._
import DefaultJsonProtocol._
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

case class WordClusterRequest(words: Seq[String])
case class WordTokenInfoRequest(query: String)
case class SearchRequest(env: EnvironmentState)

case class Request(name: String)
case object Request {
  implicit val format = jsonFormat1(Request.apply)
}

case class Person(name: String, age: Int)
case object Person {
  implicit val format = jsonFormat2(Person.apply)
}

trait BasicService extends HttpService {
  val staticContentRoot = "public"
  val basicRoute =
    path("") {
      get {
        getFromFile(staticContentRoot + "/index.html")
      }
    } ~
    get {
      unmatchedPath { p => getFromFile(staticContentRoot + p) }
    }
}

trait MyService extends HttpService with SprayJsonSupport {
  val serviceRoute =
    pathPrefix("api" / "foo") {
      post {
        entity(as[Request]) { body =>
          complete(Person("tony", 29))
        }
      }
    }
}

class MyActor extends Actor with BasicService with MyService {
  implicit def myExceptionHandler(implicit log: LoggingContext) =
    ExceptionHandler {
      case e: Exception =>
        requestUri { uri =>
          log.error(toString, e)
          complete(StatusCodes.InternalServerError -> e.getMessage)
        }
    }
  def actorRefFactory = context
  val cacheControlMaxAge = HttpHeaders.`Cache-Control`(CacheDirectives.`max-age`(0))
  def receive = runRoute(basicRoute ~ serviceRoute)
}

object MyWebApp {
  val name = "webapp"
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("webapp")
    val service = system.actorOf(Props[MyActor], "webapp-actor")
    
    {
      implicit val timeout = Timeout(30.seconds)
      IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = 8080)
    }
  }
}