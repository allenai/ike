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
  implicit val qwordFormat = jsonFormat1(QWord.apply)
  implicit val tableValueFormat = jsonFormat1(TableValue.apply)
  implicit val tableRowForamt = jsonFormat1(TableRow.apply)
  implicit val tableFormat = jsonFormat4(Table.apply)
  implicit val searchConfigFormat = jsonFormat2(SearchConfig.apply)
  implicit val searchRequestFormat = jsonFormat4(SearchRequest.apply)
  implicit val searchResponse = jsonFormat2(SearchResponse.apply)
  implicit val wordInfoRequest = jsonFormat2(WordInfoRequest.apply)
  implicit val wordInfoResponse = jsonFormat3(WordInfoResponse.apply)
}
