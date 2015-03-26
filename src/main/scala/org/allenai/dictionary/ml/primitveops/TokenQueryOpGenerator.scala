package org.allenai.dictionary.ml.primitveops

import nl.inl.blacklab.search.{ Hits, Hit }

abstract class TokenQueryOpGenerator {

  /** @return properties required to exist in given kwic data
    *        for the hits used in generateOperations
    */
  def requiredProperties: Seq[String]

  /** @return minimum context size required from kiwc data
    *        for the hits used in generateOperations
    */
  def requiredContextSize: Int

  /** @param hit to generate operations for
    * @param source source of hit, used to get Kwic or capture groups if needed
    * @return Sequence of operators that would allow a query to match the given hit
    */
  def generateOperations(hit: Hit, source: Hits): Seq[MarkedOp]
}
