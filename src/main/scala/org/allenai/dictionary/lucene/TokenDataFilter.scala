package org.allenai.dictionary.lucene

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute

class TokenDataFilter(stream: TokenStream) extends TokenFilter(stream) {
  val charTermAttr = addAttribute(classOf[CharTermAttribute])
  val posIncrAttr = addAttribute(classOf[PositionIncrementAttribute]);
  var first = true
  override def reset= {
    first = true;
    super.reset
  }
  override def incrementToken: Boolean = {
    if (input.incrementToken) {
      val atSep = charTermAttr.toString == IndexableSentence.tokenSep
      if (atSep || first) {
        posIncrAttr.setPositionIncrement(1)
        first = false
      } else {
        posIncrAttr.setPositionIncrement(0)
      }
      return true
    } else {
      return false
    }
  }
}