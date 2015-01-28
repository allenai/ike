package org.allenai.dictionary

import spray.json._
import DefaultJsonProtocol._

object JsonSerialization {
  implicit val wordDataFormat = jsonFormat2(WordData.apply)
  implicit val blackLabResultFormat = jsonFormat3(BlackLabResult.apply)
  implicit val keyedBlackLabResultFormat = jsonFormat2(KeyedBlackLabResult.apply)
  implicit val groupedBlackLabResultFormat = jsonFormat2(GroupedBlackLabResult.apply)
  implicit val requestFormat = jsonFormat3(SearchRequest.apply)
}
