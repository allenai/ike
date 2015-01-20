package org.allenai.dictionary.blacklab

import org.allenai.scholar.text.NamedAnnotatedText
import org.allenai.scholar.text.Sentence
import org.allenai.scholar.text.AnnotatedText
import org.allenai.scholar.text.Token
import org.allenai.scholar.text.PosTag
import org.allenai.scholar.mentions.{BrownClusterAnnotation => Cluster}
import scala.xml.Elem
import scala.xml.Text
import scala.language.implicitConversions

object BlackLabFormat {
  
  implicit def optStrToOptText(opt: Option[String]): Option[Text] = opt map Text.apply
  
  def fromToken(text: AnnotatedText, token: Token): Elem = {
    val content = text.content(token)
    val posTag = text.typedAnnotationsUnder[PosTag](token).headOption.map(_.string)
    val cluster = text.typedAnnotationsUnder[Cluster](token).headOption.map(_.clusterId)
    <token pos={posTag} cluster={cluster}>{content}</token>
  }
  
  def fromSentence(text: AnnotatedText, sentence: Sentence): Elem = {
    val tokens = text.typedAnnotationsUnder[Token](sentence)
    val tokenElems = tokens.map(fromToken(text, _))
    <sentence>{tokenElems}</sentence>
  }
  
  def fromAnnotations(named: NamedAnnotatedText) = {
    val text = named.text
    val name = named.name
    val sentences = text.typedAnnotations[Sentence]
    val sentenceElems = sentences.map(fromSentence(text, _))
    <document name={name}>{sentenceElems}</document>
  }
}