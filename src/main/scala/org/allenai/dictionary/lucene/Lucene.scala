package org.allenai.dictionary.lucene

import org.apache.lucene.util.Version
import org.apache.lucene.document.FieldType
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import scala.collection.JavaConverters._

object Lucene {
  val version = Version.LUCENE_48
  val docIdFieldName = "docId"
  val docIdFieldType = {
    val f = new FieldType
    f.setIndexed(true)
    f.setStored(true)
    f.freeze
    f
  }
  val startFieldName = "startOffset"
  val startFieldType = {
    val f = new FieldType
    f.setIndexed(false)
    f.setStored(true)
    f.freeze
    f
  }
  val endFieldName = "endOffset"
  val endFieldType = {
    val f = new FieldType
    f.setIndexed(false)
    f.setStored(true)
    f.freeze
    f
  }
  val tokenDataFieldName = "tokenData"
  val tokenDataFieldType = {
    val f = new FieldType
    f.setIndexed(true)
    f.setStored(true)
    f.setStoreTermVectors(true)
    f.setStoreTermVectorOffsets(true)
    f.freeze
    f
  }
  def tokenDataAnalyzer: TokenDataAnalyzer = new TokenDataAnalyzer
  def keywordAnalyzer: KeywordAnalyzer = new KeywordAnalyzer
  def analyzer: Analyzer = {
    val analyzerMap = Map(docIdFieldName -> keywordAnalyzer, startFieldName -> keywordAnalyzer,
        endFieldName -> keywordAnalyzer, tokenDataFieldName -> tokenDataAnalyzer)
    new PerFieldAnalyzerWrapper(keywordAnalyzer, analyzerMap.asJava)
  }
}