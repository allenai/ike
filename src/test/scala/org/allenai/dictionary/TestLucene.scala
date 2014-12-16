package org.allenai.dictionary

import scala.collection.JavaConverters._
import org.scalatest.FlatSpec
import java.nio.file.Files
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.util.Version
import org.apache.lucene.store.NIOFSDirectory
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.highlight.TokenSources
import org.apache.lucene.index.FieldInfo
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.FieldType
import org.apache.lucene.search.postingshighlight.PostingsHighlighter
import org.apache.lucene.search.highlight.SimpleHTMLFormatter
import org.apache.lucene.search.highlight.Highlighter
import org.apache.lucene.search.highlight.QueryScorer
import org.apache.lucene.analysis.en.EnglishPossessiveFilter
import org.apache.lucene.analysis.synonym.SynonymFilter
import org.apache.lucene.analysis.synonym.SynonymMap
import org.apache.lucene.analysis.en.EnglishPossessiveFilter
import org.apache.lucene.util.CharsRef
import org.apache.lucene.analysis.Analyzer
import java.io.Reader
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import org.apache.lucene.analysis.core.WhitespaceTokenizer
import org.apache.lucene.analysis.standard.StandardTokenizer
import org.apache.lucene.analysis.hunspell.HunspellStemFilter
import org.apache.lucene.analysis.core.LowerCaseFilter
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.search.highlight.Formatter
import org.apache.lucene.search.highlight.TokenGroup
import scala.collection.mutable.{Seq => MutableSeq}
import scala.collection.mutable.ListBuffer
import org.apache.lucene.search.spans.SpanQuery
import org.apache.lucene.search.spans.SpanNearQuery
import org.apache.lucene.search.spans.SpanTermQuery
import org.apache.lucene.index.Term
import org.apache.lucene.index.AtomicReaderContext
import org.apache.lucene.util.Bits
import java.util.HashMap
import org.apache.lucene.index.TermContext
import java.util.TreeMap
import org.apache.lucene.search.DocIdSetIterator
import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper
import org.apache.lucene.sandbox.queries.regex.RegexQuery



class TestLucene extends FlatSpec {
      
  val docs = "This is some super TEXT that is super slick." :: "This is super salty!" :: Nil
  val wordClusters = Map("super" -> "00", "slick" -> "01", "salty" -> "001", "text" -> "1")
    
  val tempDir = Files.createTempDirectory("lucene").toFile
  val writer = LuceneWriter(tempDir, wordClusters)
  writer.write(docs.iterator)
  val reader = LuceneReader(tempDir)
  
  "Lucene" should "search over words and clusters" in {
    val x = reader.wordQuery("super")
	val y = reader.clusterPrefixQuery("0")
	val q = reader.sequenceQuery(x :: y :: Nil)
	val results = reader.matches(q).toSet
	assert(results == Set("super slick", "super salty"))
  }
  
  it should "handle regexes" in {
    val x = reader.wordQuery("super")
	val y = reader.clusterPrefixQuery("")
	val q = reader.sequenceQuery(x :: y :: Nil)
	val results = reader.matches(q).toSet
	assert(results == Set("super slick", "super salty", "super text"))
  }
}