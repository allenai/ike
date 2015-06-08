package org.allenai.dictionary.index
import org.allenai.nlpstack.core._
import org.allenai.nlpstack.lemmatize.{ MorphaStemmer => lemmatizer }
import org.allenai.nlpstack.postag.{ defaultPostagger => postagger }
import org.allenai.nlpstack.segment.{ defaultSegmenter => segmenter }
import org.allenai.nlpstack.tokenize.{ defaultTokenizer => tokenizer }
import org.allenai.nlpstack.chunk.{ defaultChunker => chunker }

object NlpAnnotate {
  def segment(text: String): Seq[Segment] = segmenter.segment(text).toSeq
  def tokenize(segment: Segment): Seq[Token] = tokenizer.tokenize(segment.text)
  def postag(tokens: Seq[Token]): Seq[PostaggedToken] = postagger.postagTokenized(tokens)
  def chunk(tokens: Seq[PostaggedToken]): Seq[ChunkedToken] = chunker.chunkPostagged(tokens)
  def addEndingMarkers(tokens: Seq[ChunkedToken]): Seq[ChunkedToken] = {
    if (tokens.isEmpty) List()
    else {
      (tokens.sliding(2).toList :+ Seq(tokens.last)).map {
        case Seq(ChunkedToken(a, b, c, d), ChunkedToken(x, _, _, _)) if (a.startsWith("I-") && x.startsWith("B-")) => ChunkedToken("E-" + a.substring(2), b, c, d)
        case Seq(ChunkedToken(a, b, c, d), ChunkedToken(x, _, _, _)) if (a.startsWith("B-") && x.startsWith("B-")) => ChunkedToken("BE-" + a.substring(2), b, c, d)
        case Seq(ChunkedToken(a, b, c, d), ChunkedToken(x, _, _, _)) => ChunkedToken(a, b, c, d)
        case Seq(ChunkedToken(a, b, c, d)) if a.startsWith("B-") => ChunkedToken("BE-" + a.substring(2), b, c, d)
        case Seq(ChunkedToken(a, b, c, d)) => ChunkedToken(a, b, c, d)
      }
    }
  }
  def lemmatize(chunked: Seq[ChunkedToken]): Seq[Lemmatized[ChunkedToken]] =
    chunked.map {
      case x => lemmatizer.lemmatizePostaggedToken(x)
    }
  def annotate(text: String): Seq[Seq[Lemmatized[ChunkedToken]]] = segment(text).map { segment =>
    val tokens = tokenize(segment)
    val tagged = postag(tokens)
    val chunked = chunk(tagged)
    val chunkedWithEndingMarkers = addEndingMarkers(chunked)
    lemmatize(chunkedWithEndingMarkers)
  }
}

