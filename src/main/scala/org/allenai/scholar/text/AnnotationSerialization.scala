package org.allenai.scholar.text

import spray.json._
import DefaultJsonProtocol._
import org.allenai.common.json._

/** An object that defines the JSON serialization behavior of Annotation objects. If you create a
  * new annotation type that you wish to serialize to JSON, you must modify this file. Follow
  * the three steps outlined in the comments.
  */
object AnnotationSerialization {

  // Step 1: Define a JSON formatter for your Annotation class.
  // These are regular spray json formats, but updated to read/write a "type" field. The pack
  // method comes from the allenai common library.
  implicit val sentenceFormat = jsonFormat1(Sentence.apply).pack("type" -> "Sentence")
  implicit val tokenFormat = jsonFormat1(Token.apply).pack("type" -> "Token")
  implicit val posTagFormat = jsonFormat2(PosTag.apply).pack("type" -> "PosTag")
  implicit val lemmaFormat = jsonFormat2(Lemma.apply).pack("type" -> "Lemma")
  implicit val titleFormat = jsonFormat1(Title.apply).pack("type" -> "Title")
  implicit val abstractFormat = jsonFormat1(Abstract.apply).pack("type" -> "Abstract")
  implicit val sectionTitleFormat = jsonFormat2(SectionHeader.apply).pack("type" -> "SectionHeader")
  implicit val sectionBodyFormat = jsonFormat2(SectionBody.apply).pack("type" -> "SectionBody")
  implicit val wordClusterFormat =
    jsonFormat2(WordCluster.apply).pack("type" -> "BrownClusterAnnotation")

  // Step 2: Add your JSON format to this sequence of unpackers.
  // An implicit collection of all the formats, which is used to deserialize.
  implicit val unpackers = Seq(sentenceFormat, tokenFormat, posTagFormat, lemmaFormat, titleFormat,
    abstractFormat, sectionTitleFormat, sectionBodyFormat, wordClusterFormat)

  // The root format for all Annotation objects.
  implicit object AnnotationFormat extends RootJsonFormat[Annotation] {

    // Step 3: Create a new case for your case class.
    // Have to manually match against all known subtypes...
    override def write(annotation: Annotation): JsValue = annotation match {
      case a: Sentence => a.toJson
      case a: Token => a.toJson
      case a: PosTag => a.toJson
      case a: Lemma => a.toJson
      case a: Title => a.toJson
      case a: Abstract => a.toJson
      case a: SectionHeader => a.toJson
      case a: SectionBody => a.toJson
      case a: WordCluster => a.toJson
      case _ => throw new IllegalArgumentException(s"Unexpected annotation type: $annotation")
    }

    // Use the unpacking functionality from allenai common to deserialize.
    override def read(jsValue: JsValue): Annotation = jsValue.asJsObject.unpackAs[Annotation]
  }
}
