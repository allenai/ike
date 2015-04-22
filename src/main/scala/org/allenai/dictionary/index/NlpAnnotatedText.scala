package org.allenai.dictionary.index

import org.allenai.nlpstack.core.{ Lemmatized, PostaggedToken }

case class NlpAnnotatedText(idText: IdText, sentences: Seq[Seq[Lemmatized[PostaggedToken]]])
