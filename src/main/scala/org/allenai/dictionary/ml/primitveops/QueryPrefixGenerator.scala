package org.allenai.dictionary.ml.primitveops

import nl.inl.blacklab.search.{Hits, Hit}

/**
 * Generates applicable Prefix operators for a given hit
 *
 * @param leafs What QLeaf operators to generate for each prefix token
 * @param indices What tokens to generate prefixes for, where '1' indicates the
 *                token before the hit, 2 the second token before the hit, ect.
 */
case class QueryPrefixGenerator(leafs: QLeafGenerator, indices: Seq[Int])
  extends TokenQueryOpGenerator {

  indices.foreach(i => require(i >= 1))

  override def generateOperations(hit: Hit, source: Hits): Seq[MarkedOp]  = {
    val kwic = source.getKwic(hit)
    indices.filter(_ <= kwic.getHitStart).flatMap(index => {
      val slot = Prefix(index)
      val offset = kwic.getHitStart - index
      leafs.generate(kwic, offset).map(q => MarkedOp(SetToken(slot, q), false))
    })
  }

  override def requiredProperties: Seq[String] = leafs.getRequiredProperties
  override def requiredContextSize: Int = indices.max
}
