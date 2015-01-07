package org.allenai.dictionary.lucene

case class Attribute(key: String, value: String) {
  assert(!key.contains(" "), s"Key cannot contain spaces: '$key'")
  assert(!value.contains(" "), s"Value cannot contain spaces: '$value'")
  assert(key != DocToken.contentString, s"Attribute cannot have key '${DocToken.contentString}'")
  override def toString: String = s"$key=$value"
}

case class DocToken(content: String, attributes: Seq[Attribute]) {
  assert(!content.contains(" "), s"Content cannot contain spaces: '$content'")
  override def toString: String = {
    val contentString = s"${DocToken.contentString}=$content"
    val parts = contentString +: attributes.map(_.toString)
    parts.mkString(" ")
  }
}

case object DocToken {
  def contentString = "CONTENT"
  def isContent(s: String): Boolean = s.startsWith(s"$contentString=")
}
