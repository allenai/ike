package org.allenai.scholar.text

import spray.json._
import DefaultJsonProtocol._
import org.allenai.common.immutable.Interval

/** This object controls how AnnotatedText objects are serialized to JSON. Because AnnotatedText
  * is not a case class, the standard spray jsonFormatXXX functions cannot be used. Instead, a
  * custom JsonFormat object is defined.
  */
object AnnotatedTextSerialization {

  // Controls how annotations are serialized, which is needed to serialize AnnotatedText objects
  import AnnotationSerialization._

  // These are the JSON object field names used to represent an AnnotatedText object.
  val contentKey = "content"
  val annotationsKey = "annotations"

  implicit object AnnotatedTextFormat extends RootJsonFormat[AnnotatedText] {

    // Returns a JSON object {contentField: "...", annotationsField: [...]}
    override def write(text: AnnotatedText): JsValue = {
      val contentJs = JsString(text.content)
      val annotationsJs = text.annotations.map(_.toJson)
      JsObject(contentKey -> contentJs, annotationsKey -> JsArray(annotationsJs: _*))
    }

    // Reads the above format via pattern matching
    override def read(value: JsValue): AnnotatedText = {
      val fields = value.asJsObject.fields
      val content = fields.get(contentKey) match {
        case Some(JsString(content)) => content
        case _ => throw readError(contentKey, value)
      }
      val annotations = fields.get(annotationsKey) match {
        case Some(JsArray(jsAnnotations)) => jsAnnotations.map(_.convertTo[Annotation])
        case _ => throw readError(annotationsKey, value)
      }
      new AnnotatedText(content, annotations)
    }

    private def readError(key: String, value: JsValue): Exception =
      new IllegalArgumentException(s"Could not read field $key in $value")
  }
}
