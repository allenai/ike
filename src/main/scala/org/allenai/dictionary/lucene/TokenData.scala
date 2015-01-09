package org.allenai.dictionary.lucene

case class Attribute(key: String, value: String) {
  assert(Attribute.legalString(key), s"Illegal key: $key")
  assert(Attribute.legalString(value), s"Illegal value: $value")
  val sep = Attribute.sep
  override def toString: String = s"$key$sep$value"
}
case object Attribute {
  val sep = "="
  def fromString(s: String): Attribute = s.split(sep, 2) match {
    case Array(key, value) => Attribute(key.trim, value.trim)
    case _ => throw new IllegalArgumentException(s"Could not parse attribute: $s")
  }
  def legalString(s: String): Boolean =
    !s.contains(sep) && !s.contains(TokenData.sep) && !s.contains(TokenDataSeq.sep)
  def clean(s: String): String =
    s.replaceAllLiterally(TokenData.sep, "").replaceAllLiterally(TokenDataSeq.sep, "").replaceAllLiterally(Attribute.sep, "")
}

case class TokenData(attributes: Seq[Attribute]) {
  override def toString: String = attributes.mkString(TokenData.sep)
}

case object TokenData {
  val sep = " "
  def fromString(s: String): TokenData = {
    val parts = s.split(sep)
    val attrs = parts.map(Attribute.fromString)
    TokenData(attrs)
  }
}

case class TokenDataSeq(data: Seq[TokenData]) {
  override def toString: String = s"${TokenDataSeq.sep} " + data.mkString(s" ${TokenDataSeq.sep} ")
}

case object TokenDataSeq {
  val sep = "|"
  def fromString(s: String): TokenDataSeq = {
    val parts = s.split(sep)
    val data = parts.map(TokenData.fromString)
    TokenDataSeq(data)
  }
}
