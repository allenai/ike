package org.allenai.dictionary

import spray.json._
import DefaultJsonProtocol._
import org.allenai.common.immutable.Interval

object JsonSerialization {
  implicit val wordDataFormat = jsonFormat2(WordData.apply)
  implicit val blackLabResultFormat = jsonFormat3(BlackLabResult.apply)
  implicit val keyedBlackLabResultFormat = jsonFormat2(KeyedBlackLabResult.apply)
  implicit val groupedBlackLabResultFormat = jsonFormat3(GroupedBlackLabResult.apply)
  implicit val dictionaryFormat = jsonFormat3(Dictionary.apply)
  implicit val searchRequestFormat = jsonFormat5(SearchRequest.apply)
  implicit val parseRequestFormat = jsonFormat1(ParseRequest.apply)
  implicit object queryNodeFormat extends RootJsonFormat[QueryNode] {
    def write(qn: QueryNode): JsValue = {
      val children: JsValue = qn.children match {
        case Nil => JsArray()
        case lst => JsArray((lst map write): _*)
      }
      JsObject(
        "name" -> qn.name.toJson,
        "interval" -> qn.interval.toJson,
        "data" -> qn.data.toJson,
        "children" -> children
      )
    }
    def read(value: JsValue): QueryNode = {
      val fields = value.asJsObject.fields
      val result = for {
        name <- fields.get("name").map(_.convertTo[String])
        interval <- fields.get("interval").map(_.convertTo[Interval])
        data <- fields.get("data").map(_.convertTo[Map[String, String]])
        childrenJsVal <- fields.get("children")
        children <- childrenJsVal match {
          case arr: JsArray => Some(arr.elements.map(read))
          case _ => None
        }
        node = QueryNode(name, interval, children, data)
      } yield node
      result match {
        case Some(qnode) => qnode
        case None => throw new DeserializationException("QueryNode expected")
      }
    }
  }
}
