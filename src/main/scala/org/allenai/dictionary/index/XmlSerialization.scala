package org.allenai.dictionary.index

import scala.xml.Elem
import scala.xml.Text
import scala.xml.Node

object XmlSerialization {
  def xml(text: IndexableText): Elem = {
    val children = addSpaces(text.sentences map xml)
    <document>{children}</document>
  }
  def xml(tokens: Seq[IndexableToken]): Elem = {
    val children = addSpaces(tokens map xml)
    <sentence>{children}</sentence>
  }
  def xml(token: IndexableToken): Elem =
    <word pos={token.pos} cluster={token.cluster} lemma={token.lemma}>{token.word}</word>
  def addSpaces(elems: Seq[Elem]): Seq[Node] = {
    val n = elems.size
    val spaces = List.fill(n)(Text(" "))
    for {
      (elem, space) <- elems.zip(spaces)
      node <- List(elem, space)
    } yield node
  }
}
