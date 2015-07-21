package org.allenai.dictionary.index

import org.allenai.nlpstack.chunk.{ defaultChunker => chunker }
import org.allenai.nlpstack.core._
import org.allenai.nlpstack.lemmatize.{ MorphaStemmer => lemmatizer }
import org.allenai.nlpstack.postag.{ defaultPostagger => postagger }
import org.allenai.nlpstack.segment.{ defaultSegmenter => segmenter }
import org.allenai.nlpstack.tokenize.{ defaultTokenizer => tokenizer }

import scala.util.control.NonFatal

object NlpAnnotate {
  def segment(text: String): Seq[Segment] = segmenter.segment(text).toSeq

  def tokenize(segment: Segment): Seq[Token] = tokenizer.tokenize(segment.text)

  def postag(tokens: Seq[Token]): Seq[PostaggedToken] = postagger.postagTokenized(tokens)

  def chunk(tokens: Seq[PostaggedToken]): Seq[ChunkedToken] = chunker.chunkPostagged(tokens)

  def addEndingMarkers(tokens: Seq[ChunkedToken]): Seq[ChunkedToken] = {
    if (tokens.isEmpty) {
      List()
    } else {
      def swI(x: String) = x.startsWith("I-")
      def swB(x: String) = x.startsWith("B-")

      (tokens.sliding(2).toList :+ Seq(tokens.last)).map {
        case Seq(ChunkedToken(a, b, c, d), ChunkedToken(x, _, _, _)) if swI(a) && swB(x) =>
          ChunkedToken("E-" + a.substring(2), b, c, d)
        case Seq(ChunkedToken(a, b, c, d), ChunkedToken(x, _, _, _)) if swB(a) && swB(x) =>
          ChunkedToken("BE-" + a.substring(2), b, c, d)
        case Seq(ChunkedToken(a, b, c, d), ChunkedToken(x, _, _, _)) =>
          ChunkedToken(a, b, c, d)
        case Seq(ChunkedToken(a, b, c, d)) if swB(a) =>
          ChunkedToken("BE-" + a.substring(2), b, c, d)
        case Seq(ChunkedToken(a, b, c, d)) =>
          ChunkedToken(a, b, c, d)
      }
    }
  }

  def lemmatize(chunked: Seq[ChunkedToken]): Seq[Lemmatized[ChunkedToken]] =
    chunked.map(lemmatizer.lemmatizePostaggedToken)

  def annotate(text: String): Seq[Seq[Lemmatized[ChunkedToken]]] = segment(text).flatMap {
    segment =>
      val tokens = tokenize(segment)
      val tagged = postag(tokens)
      try {
        val chunked = chunk(tagged)
        val chunkedWithEndingMarkers = addEndingMarkers(chunked)
        Some(lemmatize(chunkedWithEndingMarkers))
      } catch {
        case NonFatal(e) => None
      }
  }
}

