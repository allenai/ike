package org.allenai.dictionary.ml.primitveops

import nl.inl.blacklab.search._

/** Generates Suffix operators that could be applied to a given hit
  *
  * @param leafs What QLeafs to generate for each suffix
  * @param indices Indices of tokens to generated tokens for, the token
  *       immediately after that hit indexed as '1', then '2' ect.
  */
case class SuffixOpGenerator(leafs: QLeafGenerator, indices: Seq[Int])
    extends TokenQueryOpGenerator {

  indices.foreach(i => require(i >= 1))

  override def generateOperations(hit: Hit, source: Hits): Seq[MarkedOp] = {
    val kwic = source.getKwic(hit)
    val words = kwic.getTokens("word")
    // Due to a hack in BlackLab, sometimes kwic can padded by a blank word
    val extraTag = if (words.get(words.size() - 1) == "") 1 else 0
    val numSuffixTokens = words.size - extraTag - kwic.getHitEnd
    indices.filter(_ <= numSuffixTokens).flatMap(index => {
      val slot = Suffix(index)
      val kwic = source.getKwic(hit)
      val offset = kwic.getHitEnd + index - 1
      leafs.generate(kwic, offset).map(q => MarkedOp(SetToken(slot, q), required = false))
    })
  }

  override val requiredProperties: Seq[String] = leafs.getRequiredProperties
  override val requiredContextSize: Int = indices.max
}
