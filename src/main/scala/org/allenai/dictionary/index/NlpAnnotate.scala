package org.allenai.dictionary.index
import org.allenai.nlpstack.segment.{defaultSegmenter => segmenter}
import org.allenai.nlpstack.tokenize.{defaultTokenizer => tokenizer}
import org.allenai.nlpstack.postag.{defaultPostagger => postagger}
import org.allenai.nlpstack.lemmatize.{MorphaStemmer => lemmatizer}
import org.allenai.nlpstack.core.Segment
import org.allenai.nlpstack.core.Token
import org.allenai.nlpstack.core.PostaggedToken
import org.allenai.nlpstack.core.Lemmatized

object NlpAnnotate {
  def segment(text: String): Seq[Segment] = segmenter.segment(text).toSeq
  def tokenize(segment: Segment): Seq[Token] = tokenizer.tokenize(segment.text)
  def postag(tokens: Seq[Token]): Seq[PostaggedToken] = postagger.postagTokenized(tokens)
  def lemmatize(postagged: Seq[PostaggedToken]): Seq[Lemmatized[PostaggedToken]] =
    postagged map lemmatizer.lemmatizePostaggedToken
  def annotate(text: String): Seq[Seq[Lemmatized[PostaggedToken]]] = for {
    seg <- segment(text)
    tokens = tokenize(seg)
    tagged = postag(tokens)
    lemmatized = lemmatize(tagged)
  } yield lemmatized
}
