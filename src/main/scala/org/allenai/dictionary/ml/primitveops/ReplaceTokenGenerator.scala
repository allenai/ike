package org.allenai.dictionary.ml.primitveops

import nl.inl.blacklab.search.{ Hit, Hits }
import org.allenai.dictionary._

object ReplaceTokenGenerator {

  /** Build a ReplaceTokenGenerator that will propose SetToken operators
    * that make the given query strictly more specific.
    */
  def specifyTokens(queryAsTokens: Seq[QExpr], indices: Seq[Int],
    properties: Seq[String],
    clusterSizes: Seq[Int] = Seq()): ReplaceTokenGenerator = {
    require(indices.max <= queryAsTokens.size)
    require(clusterSizes.size == 0 || clusterSizes.min >= 1)
    val indexLeafMap = indices.flatMap(index => {
      val qexpr = queryAsTokens(index - 1)
      val (props, clusters) = qexpr match {
        case QWildcard() => (Seq("pos", "word"), clusterSizes)
        case QPos(_) => (Seq("word"), Seq())
        case QCluster(cluster) => (Seq("word"), clusterSizes.filter(_ > cluster.length))
        case _ => (Seq(), Seq())
      }
      val propsToKeep = props.filter(properties contains _).toSet
      if (propsToKeep.size + clusters.size == 0) {
        None
      } else {
        Some((index, QLeafGenerator(propsToKeep, clusters.toSet)))
      }
    })
    new ReplaceTokenGenerator(indexLeafMap)
  }
}

/** Generates SetToken operations for tokens within a hit.
  *
  * @param targets list of indices inside the hit to generate
  *            operators for, paired with the QLeafGenerator to use.
  */
case class ReplaceTokenGenerator(targets: Seq[(Int, QLeafGenerator)])
    extends TokenQueryOpGenerator {

  require(targets.forall(_._1 >= 1))

  override def generateOperations(hit: Hit, source: Hits): Seq[MarkedOp] = {
    val kwic = source.getKwic(hit)
    targets.flatMap {
      case (index, leafs) =>
        val slot = Match(index)
        val kwicIndex = kwic.getHitStart + index - 1
        leafs.generate(kwic, kwicIndex).
          map(leaf => MarkedOp(SetToken(slot, leaf), required = false))
    }
  }

  override val requiredProperties = targets.flatMap(_._2.getRequiredProperties).
    toSet.toSeq
  override val requiredContextSize: Int = 0
}
