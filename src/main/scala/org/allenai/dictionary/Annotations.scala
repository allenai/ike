package org.allenai.dictionary

import org.allenai.scholar.text.AnnotatedText
import org.allenai.scholar.text.Token
import org.allenai.scholar.text.WordCluster
import org.allenai.scholar.pipeline.annotate.SentenceAnnotator
import org.allenai.scholar.text.NamedAnnotatedText
import org.allenai.scholar.pipeline.annotate.TokenAnnotator
import scala.io.Source

object Annotations {
  
  def clusterFromString(s: String): (String, String) = s.split("\t") match {
    case Array(id, word, count) => (word, id)
    case _ => throw new IllegalArgumentException(s"Invalid cluster row: $s")
  }

  def readClusters(path: String): Map[String, String] =
    Source.fromFile(path).getLines.map(clusterFromString).toMap
  
  def annotate(clusters: Map[String, String], content: String): AnnotatedText = {
    val empty = new AnnotatedText(content, Seq.empty)
    val sentenced = SentenceAnnotator.annotate(NamedAnnotatedText("", empty))
    val tokenized = TokenAnnotator.annotate(sentenced).text
    annotateClusters(clusters, tokenized)
  }
  
  def annotateClusters(clusters: Map[String, String], text: AnnotatedText): AnnotatedText = {
    val anns = for {
      token <- text.typedAnnotations[Token]
      cluster <- clusters.get(text.content(token))
      ann = WordCluster(token.interval, cluster)
    } yield ann
    text.update(anns)
  }

}