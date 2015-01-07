package org.allenai.dictionary.lucene

import org.allenai.scholar.text.AnnotatedText
import org.allenai.scholar.text.Token
import org.allenai.scholar.text.PosTag
import org.allenai.scholar.mentions.{BrownClusterAnnotation => Cluster}
import org.allenai.scholar.text.Annotation
import org.allenai.scholar.text.Sentence

object Text {
  val posTagAttr = "POS"
  val clusterTagAttr = "CLUSTER"
  def docToken(token: Token, text: AnnotatedText): DocToken = {
    import text.typedAnnotationsUnder
    val content = text.content(token).replaceAll(s"\\s+", "") 
    val posTag = typedAnnotationsUnder[PosTag](token).headOption map {
      p => Attribute(posTagAttr, p.string)
    }
    val cluster = typedAnnotationsUnder[Cluster](token).headOption map {
      c => Attribute(clusterTagAttr, c.clusterId)
    }
    val attrs = Seq(posTag, cluster).flatten
    DocToken(content, attrs)
  }
  def fromAnnotatedText(text: AnnotatedText): Seq[String] = for {
    sent <- text.typedAnnotations[Sentence]
    tokens = text.typedAnnotationsUnder[Token](sent)
    docTokens = tokens.map(docToken(_, text))
    sentString = docTokens.mkString(" ")
  } yield sentString
}