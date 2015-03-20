package org.allenai.dictionary.ml.primitveops

import nl.inl.blacklab.search.Kwic
import org.allenai.dictionary._

object QLeafGenerator {

  val blacklistWords = Set(".", ",", "{", "}", "(", ")", "+", "*")
  def validWord(word: String): Boolean = {
    !(blacklistWords contains word)
  }

  val blackListPos = Set(".", ",", ")", "(", ":")
  def validPos(pos: String): Boolean = {
    !(blackListPos contains pos)
  }

  def propertyValueToLeaf(property: String, value: String): Option[QLeaf] = {
    property match {
      case "word" if validWord(value) => Some(QWord(value))
      case "word" => None
      case "pos" if validPos(value) => Some(QPos(value))
      case "pos" => None
      case _ => throw new IllegalArgumentException(property + " is not a valid property")
    }
  }
}

/** Class that generates QLeafs that would match a particular token in a sentence.
  *
  * @param properties to generate leaves for
  * @param clusterSizes cluster sizes to generate QClusters for
  * @param wildCard whether to generate QWildcard expressions
  */
case class QLeafGenerator(
    properties: Set[String],
    clusterSizes: Set[Int] = Set(),
    wildCard: Boolean = false
) {

  def generate(kwic: Kwic, index: Int): Seq[QLeaf] = {
    val clusters = if (clusterSizes.isEmpty) {
      Seq()
    } else {
      val cluster = kwic.getTokens("cluster").get(index)
      clusterSizes.filter(_ <= cluster.length).map(backoff =>
        QCluster(cluster.substring(0, backoff)))
    }
    val otherProps = properties.map(property => {
      val token = kwic.getTokens(property).get(index)
      QLeafGenerator.propertyValueToLeaf(property, token)
    }).flatten
    val withWildCard =
      if (wildCard) (otherProps + QWildcard()) else otherProps
    (withWildCard ++ clusters).toSeq
  }

  def getRequiredProperties: Seq[String] = {
    if (clusterSizes.isEmpty) properties.toSeq else "cluster" +: properties.toSeq
  }

  override def toString: String = {
    val leafStr = properties ++ clusterSizes.map(i => "C" + i)
    s"QLeaves<${leafStr.mkString(",")}>"
  }
}
