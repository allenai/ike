package org.allenai.dictionary

import org.apache.lucene.analysis.Analyzer
import java.io.Reader
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import org.apache.lucene.analysis.standard.StandardTokenizer
import org.apache.lucene.analysis.core.LowerCaseFilter

class ClusterAnalyzer(wordClusters: Map[String, String]) extends Analyzer {
  override def createComponents(fieldName: String, reader: Reader): TokenStreamComponents = {
    val source = new StandardTokenizer(Lucene.version, reader)
    val lowercase = new LowerCaseFilter(Lucene.version, source)
    val filter = new ClusterTokenFilter(lowercase, wordClusters)
    new TokenStreamComponents(source, filter)
  }
}

class PlainAnalyzer extends Analyzer {
  override def createComponents(fieldName: String, reader: Reader): TokenStreamComponents = {
    val source = new StandardTokenizer(Lucene.version, reader)
    val lowercase = new LowerCaseFilter(Lucene.version, source)
    new TokenStreamComponents(source, lowercase)
  }
}