package org.allenai.dictionary.ml.primitveops

import nl.inl.blacklab.search.{ Hit, Hits }

/** Generates MarkedOps for all tokens in a hit. Uses the capture groups
  * stored by FuzzySequenceQuery to determine which of the proposed operators
  * should be marked as required or not.
  *
  * @param setToken used to generate SetToken ops
  * @param addToken used to generate AddToken ops
  * @param captureIndices the indices to find the capture groups where the
  *                   required edits have been (see FuzzySequenceQuery)
  */
case class RequiredEditsGenerator(
  setToken: QLeafGenerator,
  addToken: QLeafGenerator,
  captureIndices: Seq[Int]
)
    extends TokenQueryOpGenerator {

  override def generateOperations(hit: Hit, source: Hits): Seq[MarkedOp] = {
    val kwic = source.getKwic(hit)
    val captures = source.getCapturedGroups(hit)
    val indicesToSet = captureIndices.flatMap(i => {
      val capture = captures(i)
      if (capture == null) None else Some(capture.start - hit.start)
    }).toSet
    val matchSize = kwic.getMatch("word").size
    Range(0, matchSize).flatMap(i => {
      val kwicIndex = kwic.getHitStart + i
      val required = indicesToSet contains i
      val setTokens = setToken.generate(kwic, kwicIndex).
        map(leaf => MarkedOp(SetToken(Match(i + 1), leaf), required))
      val addTokens = addToken.generate(kwic, kwicIndex).
        map(leaf => MarkedOp(AddToken(i + 1, leaf), required))
      setTokens ++ addTokens
    })
  }

  override val requiredProperties = {
    (setToken.getRequiredProperties ++
      addToken.getRequiredProperties :+ "word").toSet.toSeq
  }
  override def requiredContextSize: Int = 0
}
