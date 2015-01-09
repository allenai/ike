package org.allenai.dictionary.lucene

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import java.io.Reader
import org.apache.lucene.analysis.pattern.PatternTokenizer
import java.util.regex.Pattern
import org.apache.lucene.analysis.core.WhitespaceTokenizer

class TokenDataAnalyzer extends Analyzer {
  override def createComponents(fieldName: String, reader: Reader): TokenStreamComponents = {
    val source = new WhitespaceTokenizer(Lucene.version, reader)
    val filter = new TokenDataFilter(source)
    new TokenStreamComponents(source, filter)
  }
}