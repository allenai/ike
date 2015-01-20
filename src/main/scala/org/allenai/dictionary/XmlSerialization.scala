package org.allenai.dictionary

import scala.xml.UnprefixedAttribute
import scala.xml.Elem
import scala.xml.Null
import scala.language.implicitConversions
import scala.language.reflectiveCalls
import scala.xml.NodeSeq.seqToNodeSeq
import scala.xml.Node

object XmlSerialization {
  // Makes working with xml easier
  import scala.language.reflectiveCalls
  import scala.language.implicitConversions
  implicit def pimp(elem: Elem) = new {
    def %(attrs:Map[String,String]) = {
      val seq = for( (n,v) <- attrs ) yield new UnprefixedAttribute(n, v, Null)
      (elem /: seq) ( _ % _ )
    }
  }
  def attrMap(n: Node): Map[String, String] = n.attributes.map(a => (a.key, a.value.text)).toMap
  def toXml(wd: WordData): Elem = {
    val x = <word>{wd.word}</word>
    x % wd.attributes
  }
  def toXml(sentence: Seq[WordData]): Elem = {
    val datas = sentence map toXml
    <sentence>{datas}</sentence>
  }
  def toXml(bld: BlackLabDocument): Elem = {
    val sentences = bld.sentences map toXml
    <document name={bld.name}>{sentences}</document>
  }
  def blackLabDocument(n: Node): BlackLabDocument = n match {
    case <document/> =>
      val name = attrMap(n).get("name") match {
        case Some(value) => value
        case _ => throw new IllegalArgumentException(s"Document must have name in $n")
      }
      val sentences = (n \ "sentence") map sentence
      BlackLabDocument(name, sentences)
    case _ => throw new IllegalArgumentException(s"Expected <document/> got $n")
  }
  def sentence(n: Node): Seq[WordData] = n match {
    case <sentence/> => (n \ "word") map wordData 
    case _ => throw new IllegalArgumentException(s"Expected <sentence/> got $n")
  }
  def wordData(n: Node): WordData = n match {
    case <word>{wordValue}</word> => WordData(wordValue.text, attrMap(n))
    case _ => throw new IllegalArgumentException(s"Expected <word/>, got: $n")
  }
}