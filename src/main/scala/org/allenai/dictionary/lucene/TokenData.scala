package org.allenai.dictionary.lucene

case class Attribute(key: String, value: String) {
  assert(Attribute.legalKey(key), s"Illegal key: $key")
  assert(Attribute.legalValue(value), s"Illegal value: $value")
  val sep = Attribute.sep
  override def toString: String = s"$key$sep$value"
}
case object Attribute {
  val sep = "="
  def fromString(s: String): Attribute = s.split(sep, 2) match {
    case Array(key, value) => Attribute(key.trim, value.trim)
    case _ => throw new IllegalArgumentException(s"Could not parse attribute: '$s'")
  }
  def legalKey(s: String): Boolean = legalValue(s) && !s.contains(sep)
  def legalValue(s: String): Boolean = !s.contains(TokenData.sep) 
}

case class TokenData(attributes: Seq[Attribute]) {
  override def toString: String = attributes.mkString(TokenData.sep)
}

case object TokenData {
  val sep = " "
  def fromString(s: String): TokenData = {
    val parts = s.split(sep).map(_.trim).filterNot(_ == "")
    val attrs = parts.map(Attribute.fromString)
    TokenData(attrs)
  }
}
