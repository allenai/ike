package org.allenai.scholar.text

import spray.json._
import DefaultJsonProtocol._
import AnnotatedTextSerialization._

/** A case class for holding annotated text with a string name.
  */
case class NamedAnnotatedText(name: String, text: AnnotatedText)

case object NamedAnnotatedText {
  implicit val JsFormat = jsonFormat2(NamedAnnotatedText.apply)
}
