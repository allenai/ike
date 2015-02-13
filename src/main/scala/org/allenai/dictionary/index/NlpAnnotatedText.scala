package org.allenai.dictionary.index

import org.allenai.nlpstack.core.Lemmatized
import org.allenai.nlpstack.core.PostaggedToken

case class NlpAnnotatedText(idText: IdText, sentences: Seq[Seq[Lemmatized[PostaggedToken]]])
