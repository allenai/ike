package org.allenai.dictionary

import spray.json.DefaultJsonProtocol._
import spray.json._

object JsonSerialization {
  implicit val qexprFormat = QExprJsonSerialization.QExprFormat
  implicit val wordDataFormat = jsonFormat2(WordData.apply)
  implicit val blackLabResultFormat = jsonFormat4(BlackLabResult.apply)
  implicit val keyedBlackLabResultFormat = jsonFormat2(KeyedBlackLabResult.apply)
  implicit val groupedBlackLabResultFormat = jsonFormat3(GroupedBlackLabResult.apply)
  implicit val qwordFormat = jsonFormat1(QWord.apply)
  implicit val tableValueFormat = jsonFormat1(TableValue.apply)
  implicit val tableRowForamt = jsonFormat2(TableRow.apply)
  implicit val tableFormat = jsonFormat4(Table.apply)
  implicit val searchConfigFormat = jsonFormat2(SearchConfig.apply)
  implicit val searchRequestFormat = jsonFormat4(SearchRequest.apply)
  implicit val searchResponse = jsonFormat2(SearchResponse.apply)
  implicit val wordInfoRequest = jsonFormat2(WordInfoRequest.apply)
  implicit val wordInfoResponse = jsonFormat2(WordInfoResponse.apply)
  implicit val inferConfig = jsonFormat7(SuggestQueryConfig.apply)
  implicit val inferQueryRequest = jsonFormat5(SuggestQueryRequest.apply)
  implicit val scoredQuery = jsonFormat3(ScoredStringQuery.apply)
  implicit val inferQueryResponse = jsonFormat1(SuggestQueryResponse.apply)
  implicit val corpusDescription = jsonFormat2(CorpusDescription.apply)
}
