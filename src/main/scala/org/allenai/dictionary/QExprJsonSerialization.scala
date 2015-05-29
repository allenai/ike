package org.allenai.dictionary
import org.allenai.common.json._
import spray.json.DefaultJsonProtocol._
import spray.json._

object QExprJsonSerialization {
  implicit object QExprFormat extends RootJsonFormat[QExpr] {
    override def write(qexpr: QExpr): JsValue = qexpr match {
      case q: QWord => q.toJson
      case q: QPos => q.toJson
      case q: QDict => q.toJson
      case q: QWildcard => q.toJson
      case q: QNamed => q.toJson
      case q: QUnnamed => q.toJson
      case q: QNonCap => q.toJson
      case q: QStar => q.toJson
      case q: QPlus => q.toJson
      case q: QSeq => q.toJson
      case q: QDisj => q.toJson
      case q: QPosFromWord => q.toJson
      case q: QAnd => q.toJson
      case q: QSimilarPhrases => q.toJson
      case q: QRepetition => q.toJson
    }
    override def read(jsValue: JsValue): QExpr = jsValue.asJsObject.unpackAs[QExpr]
  }
  implicit val qwordFormat = jsonFormat1(QWord.apply).pack("type" -> "QWord")
  implicit val qposFormat = jsonFormat1(QPos.apply).pack("type" -> "QPos")
  implicit val qdictFormat = jsonFormat1(QDict.apply).pack("type" -> "QDict")
  implicit val qandFormat = jsonFormat2(QAnd.apply).pack("type" -> "QAnd")
  implicit val qwildcardFormat = new RootJsonFormat[QWildcard] {
    def write(wc: QWildcard): JsValue = JsObject()
    def read(value: JsValue): QWildcard = QWildcard()
  }.pack("type" -> "QWildcard")
  implicit val qnamedFormat = jsonFormat2(QNamed.apply).pack("type" -> "QNamed")
  implicit val qunnamedFormat = jsonFormat1(QUnnamed.apply).pack("type" -> "QUnnamed")
  implicit val qnonCapFormat = jsonFormat1(QNonCap.apply).pack("type" -> "QNonCap")
  implicit val qstarFormat = jsonFormat1(QStar.apply).pack("type" -> "QStar")
  implicit val qplusFormat = jsonFormat1(QPlus.apply).pack("type" -> "QPlus")
  implicit val qrepetitionFormat = jsonFormat3(QRepetition.apply).pack("type" -> "QRepetition")
  implicit val qseqFormat = jsonFormat1(QSeq.apply).pack("type" -> "QSeq")
  implicit val qdisjFormat = jsonFormat1(QDisj.apply).pack("type" -> "QDisj")
  implicit val qpfwFormat = jsonFormat3(QPosFromWord.apply).pack("type" -> "QPosFromWord")
  implicit val simPhraseFormat = jsonFormat2(SimilarPhrase.apply)
  implicit val qspFormat = jsonFormat3(QSimilarPhrases.apply).pack("type" -> "QSimilarPhrases")
  implicit val unpackers = Seq(qwordFormat, qposFormat, qdictFormat,
    qwildcardFormat, qnamedFormat, qunnamedFormat, qnonCapFormat, qstarFormat,
    qplusFormat, qseqFormat, qdisjFormat, qpfwFormat, qspFormat)
}
