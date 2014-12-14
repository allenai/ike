package org.allenai.dictionary

import org.allenai.scholar.text.AnnotatedText
import org.allenai.scholar.text.Token
import org.allenai.scholar.text.WordCluster
import java.io.File
import java.io.PrintWriter
import com.google.code.externalsorting.ExternalSort
import scala.io.Source

class NGrams {
  def tokenStream(text: AnnotatedText): Seq[WordWithCluster] = for {
    token <- text.typedAnnotations[Token]
    cluster <- text.typedAnnotationsUnder[WordCluster](token)
    tokenString = text.content(token).replaceAll("[\\t\\n]", " ")
    clusterString = cluster.clusterId.replaceAll("[\\t\\n]", " ")
  } yield WordWithCluster(tokenString, clusterString)
  def grams(n: Int, wwcs: Seq[WordWithCluster]): Seq[NGram] = for {
    i <- 1 until n
    gram <- wwcs.sliding(i)
  } yield NGram(gram)
  def count(texts: Iterator[AnnotatedText], n: Int): Iterator[Counted[NGram]] = {
    val unsortedGrams = for {
      text <- texts
      tokens = tokenStream(text)
      gram <- grams(n, tokens)
    } yield gram
    val sortedGrams = Counting.sort(unsortedGrams map NGram.toTsv) map NGram.fromTsv
    Counting.uniqc(sortedGrams)
  }
  
}

case class NGram(grams: Seq[WordWithCluster])
case object NGram {
  def fromTsv(s: String): NGram = {
    val grams = s.split("\t").grouped(2).toSeq.collect {
      case Array(w: String, c: String) => WordWithCluster(w, c)
    }
    NGram(grams)
  }
  def toTsv(ngram: NGram): String =
    ngram.grams.map(x => s"${x.word}\t${x.cluster}").mkString("\t")
}
case class WordWithCluster(word: String, cluster: String)


