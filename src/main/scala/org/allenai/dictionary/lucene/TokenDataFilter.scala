package org.allenai.dictionary.lucene

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute

class TokenDataFilter(stream: TokenStream) extends TokenFilter(stream) {
  val charTermAttr = addAttribute(classOf[CharTermAttribute])
  val posIncrAttr = addAttribute(classOf[PositionIncrementAttribute]);
  override def incrementToken: Boolean = {
    if (input.incrementToken) {
      val term = charTermAttr.toString
      if (term == TokenDataSeq.sep) {
        posIncrAttr.setPositionIncrement(1)
      } else {
        posIncrAttr.setPositionIncrement(0)
      }
      charTermAttr.setEmpty.append(term)
      return true
    } else {
      return false
    }
  }
}