package org.allenai.dictionary.lucene

import org.allenai.common.immutable.Interval
import org.allenai.scholar.text.AnnotatedText
import org.allenai.scholar.text.Token
import org.allenai.scholar.text.PosTag
import org.allenai.scholar.mentions.{BrownClusterAnnotation => Cluster}
import org.allenai.scholar.text.NamedAnnotatedText
import org.allenai.scholar.text.Sentence
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field

case class IndexableSentence(data: TokenDataSeq, docId: String, docOffset: Interval)

case object IndexableSentence {
  val contentAttr = "CONTENT"
  val posTagAttr = "POS"
  val clusterTagAttr = "CLUSTER"
  def makeTokenData(token: Token, text: AnnotatedText): TokenData = {
    import text.typedAnnotationsUnder
    val content = Attribute(contentAttr, Attribute.clean(text.content(token)))
    val posTag = typedAnnotationsUnder[PosTag](token).headOption map {
      p => Attribute(posTagAttr, Attribute.clean(p.string))
    }
    val cluster = typedAnnotationsUnder[Cluster](token).headOption map {
      c => Attribute(clusterTagAttr, Attribute.clean(c.clusterId))
    }
    val attrs = Seq(Some(content), posTag, cluster).flatten
    TokenData(attrs)
  }
  def fromText(named: NamedAnnotatedText): Seq[IndexableSentence] = for {
    sentence <- named.text.typedAnnotations[Sentence]
    tokens = named.text.typedAnnotationsUnder[Token](sentence)
    tokenData = TokenDataSeq(tokens.map(makeTokenData(_, named.text)))
    isentence = IndexableSentence(tokenData, named.name, sentence.interval)
  } yield isentence
  def toLuceneDoc(isent: IndexableSentence): Document = {
    assert(TokenDataSeq.fromString(isent.data.toString) == isent.data)
    val doc = new Document
    val tokenData = isent.data.toString
    doc.add(new Field(Lucene.tokenDataFieldName, tokenData, Lucene.tokenDataFieldType))
    doc.add(new Field(Lucene.docIdFieldName, isent.docId, Lucene.docIdFieldType))
    doc.add(new Field(Lucene.startFieldName, isent.docOffset.start.toString, Lucene.startFieldType))
    doc.add(new Field(Lucene.endFieldName, isent.docOffset.end.toString, Lucene.endFieldType))
    doc
  }
  def fromLuceneDoc(doc: Document): IndexableSentence = {
    ???
  }
}
