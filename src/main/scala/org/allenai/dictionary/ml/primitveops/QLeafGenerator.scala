package org.allenai.dictionary.ml.primitveops

import nl.inl.blacklab.search.Kwic
import org.allenai.dictionary._

import scala.util.Success

object QLeafGenerator {
  def validWord(word: String): Boolean = {
    QueryLanguage.parse(word) match {
      case Success(QWord(word)) => true
      case _ => false
    }
  }

  def validPos(pos: String): Boolean = {
    QExprParser.posTagSet contains pos
  }

  def propertyValueToLeaf(property: String, value: String): Option[QLeaf] = {
    property match {
      case "word" => {
        val lower = value.toLowerCase
        if (validWord(lower)) {
          Some(QWord(lower))
        } else {
          None
        }
      }
      case "pos" if validPos(value) => Some(QPos(value))
      case "pos" => None
      case _ => throw new IllegalArgumentException(property + " is not a valid property")
    }
  }
}

/** Class that generates QLeafs that would match a particular token in a sentence.
  *
  * @param properties to generate leaves for
  * @param wildCard whether to generate QWildcard expressions
  */
case class QLeafGenerator(
    properties: Set[String],
    wildCard: Boolean = false
) {

  def generate(kwic: Kwic, index: Int): Seq[QLeaf] = {
    val otherProps = properties.map(property => {
      val token = kwic.getTokens(property).get(index)
      QLeafGenerator.propertyValueToLeaf(property, token)
    }).flatten
    if (wildCard) (otherProps + QWildcard()).toSeq else otherProps.toSeq
  }

  def getRequiredProperties: Seq[String] = {
    properties.toSeq
  }

  override def toString: String = {
    s"QLeaves<${properties.mkString(",")}>"
  }
}
