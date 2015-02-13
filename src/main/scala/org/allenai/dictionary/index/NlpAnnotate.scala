package org.allenai.dictionary.index
import org.allenai.nlpstack.segment.defaultSegmenter
import org.allenai.nlpstack.tokenize.defaultTokenizer
import org.allenai.nlpstack.postag.defaultPostagger
import org.allenai.nlpstack.lemmatize.MorphaStemmer
import org.allenai.nlpstack.core.Segment
import org.allenai.nlpstack.core.Token
import org.allenai.nlpstack.core.PostaggedToken
import org.allenai.nlpstack.core.Lemmatized

object NlpAnnotate {
  def segment(text: String): Seq[Segment] = defaultSegmenter.segment(text).toSeq
  def tokenize(segment: Segment): Seq[Token] = defaultTokenizer.tokenize(segment.text)
  def postag(tokens: Seq[Token]): Seq[PostaggedToken] = defaultPostagger.postagTokenized(tokens)
  def lemmatize(postagged: Seq[PostaggedToken]): Seq[Lemmatized[PostaggedToken]] =
    postagged map MorphaStemmer.lemmatizePostaggedToken
  def annotate(text: String): Seq[Seq[Lemmatized[PostaggedToken]]] = for {
    seg <- segment(text)
    tokens = tokenize(seg)
    tagged = postag(tokens)
    lemmatized = lemmatize(tagged)
  } yield lemmatized
}
