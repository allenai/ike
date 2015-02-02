package org.allenai.dictionary

import spray.json._
import DefaultJsonProtocol._
import org.allenai.common.immutable.Interval

object JsonSerialization {
  implicit val qexprFormat = QExprJsonSerialization.QExprFormat
  implicit val wordDataFormat = jsonFormat2(WordData.apply)
  implicit val blackLabResultFormat = jsonFormat3(BlackLabResult.apply)
  implicit val keyedBlackLabResultFormat = jsonFormat2(KeyedBlackLabResult.apply)
  implicit val groupedBlackLabResultFormat = jsonFormat3(GroupedBlackLabResult.apply)
  implicit val dictionaryFormat = jsonFormat3(Dictionary.apply)
  implicit val searchRequestFormat = jsonFormat5(SearchRequest.apply)
  implicit val parseRequestFormat = jsonFormat1(ParseRequest.apply)
}
