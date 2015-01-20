package org.allenai.dictionary

import org.allenai.scholar.text.Token
import org.allenai.scholar.text.AnnotatedText
import org.allenai.scholar.mentions.{BrownClusterAnnotation => TokenCluster}
import org.allenai.scholar.text.{PosTag => TokenPos}
import org.allenai.scholar.text.NamedAnnotatedText
import org.allenai.scholar.text.Sentence

object AnnotatedTextConversion {
  def wordData(token: Token, text: AnnotatedText): WordData = {
    val wordValue = text.content(token)
    val posTag = text.typedAnnotationsUnder[TokenPos](token).headOption map {
      case pos => ("pos", pos.string)
    }
    val cluster = text.typedAnnotationsUnder[TokenCluster](token).headOption map {
      case cl => ("cluster", cl.clusterId)
    }
    val attrs = Seq(posTag, cluster).flatten.toMap
    WordData(wordValue, attrs)
  }
  def blackLabDocument(named: NamedAnnotatedText): BlackLabDocument = {
    val name = named.name
    val text = named.text
    val sentences = for {
      sentence <- text.typedAnnotations[Sentence]
      tokens = text.typedAnnotationsUnder[Token](sentence)
      wordDatas = tokens.map(wordData(_, text))
    } yield wordDatas
    BlackLabDocument(name, sentences)
  }
}