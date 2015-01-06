package org.allenai.dictionary

import scala.io.Source
import spray.json._
import DefaultJsonProtocol._

case class Dictionary(name: String, positive: Set[String], negative: Set[String])

case object Dictionary {
  implicit val format = jsonFormat3(Dictionary.apply)
}
