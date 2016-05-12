package org.allenai.ike.index

case class IndexableText(idText: IdText, sentences: Seq[Seq[IndexableToken]])
