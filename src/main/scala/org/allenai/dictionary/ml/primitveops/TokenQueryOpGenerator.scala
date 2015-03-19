package org.allenai.dictionary.ml.primitveops

import nl.inl.blacklab.search.{Hits, Hit}

trait TokenQueryOpGenerator {

  /**
   * @return properties required to exist in given kwic data
   */
  def requiredProperties: Seq[String]

  /**
   * @return minimum context size required from kiwc data
   */
  def requiredContextSize: Int

  /**
   * @param hit to generate operation for
   * @param source source of hit, used to get Kwic or capture groups if needed
   * @return Sequence of operators that would allow a query to match the given hit
   */
  def generateOperations(hit: Hit, source: Hits): Seq[MarkedOp]
}
