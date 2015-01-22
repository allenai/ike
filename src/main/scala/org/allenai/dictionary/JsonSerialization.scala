package org.allenai.dictionary

import spray.json._
import DefaultJsonProtocol._

object JsonSerialization {
  implicit val wordDataFormat = jsonFormat2(WordData.apply)
  implicit val blackLabResultFormat = jsonFormat3(BlackLabResult.apply)
  implicit val requestFormat = jsonFormat1(SearchRequest.apply)
}
