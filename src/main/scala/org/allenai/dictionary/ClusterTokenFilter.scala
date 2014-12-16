package org.allenai.dictionary

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute

class ClusterTokenFilter(stream: TokenStream, wordClusters: Map[String, String])
  extends TokenFilter(stream) {
  import Lucene.clusterTokenPrefix
  val charTermAttr = addAttribute(classOf[CharTermAttribute])
  val posIncrAttr = addAttribute(classOf[PositionIncrementAttribute]);
  var buffer: Option[String] = None
  override def incrementToken: Boolean = {
    if (buffer.isDefined) {
      posIncrAttr.setPositionIncrement(0)
      charTermAttr.setEmpty.append(s"${clusterTokenPrefix}${buffer.get}")
      buffer = None
      return true
    }
    if (!input.incrementToken) {
      return false
    }
    buffer = wordClusters.get(charTermAttr.toString)
    return true
  }
  override def reset {
    super.reset
    buffer = None
  }
}
