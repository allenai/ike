package org.allenai.dictionary.index

case class IndexableText(idText: IdText, sentences: Seq[Seq[IndexableToken]])
