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

class ClusterTokenFilter(stream: TokenStream, wordClusters: Map[String, String]) extends TokenFilter(stream) {
  
  val charTermAttr = addAttribute(classOf[CharTermAttribute])
  val posIncrAttr = addAttribute(classOf[PositionIncrementAttribute]);
  var buffer: Option[String] = None
  
  override def incrementToken: Boolean = {
    if (buffer.isDefined) {
      posIncrAttr.setPositionIncrement(0)
      charTermAttr.setEmpty.append(s"CLUSTER=${buffer.get}")
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

class TestLucene extends FlatSpec {
  
  "Lucene" should "do its thang" in {
    
	val tempDir = Files.createTempDirectory("lucene").toFile
	val version = Version.LUCENE_48
    val directory = new NIOFSDirectory(tempDir)
	val wordClusters = Map("super" -> "00", "slick" -> "01", "this" -> "1", "is" -> "10")
	val fieldName = "fieldname"
	val clusterFieldName = s"${fieldName}Clusters"
	
    val clusterAnalyzer = new Analyzer {
	  def createComponents(fieldName: String, reader: Reader): TokenStreamComponents = {
	    val source = new StandardTokenizer(version, reader)
	    val lowercase = new LowerCaseFilter(version, source)
	    val filter = new ClusterTokenFilter(lowercase, wordClusters)
	    new TokenStreamComponents(source, filter)
	  }
	}
	
	val plainAnalyzer = new Analyzer {
	  def createComponents(fieldName: String, reader: Reader): TokenStreamComponents = {
	    val source = new StandardTokenizer(version, reader)
	    val lowercase = new LowerCaseFilter(version, source)
	    new TokenStreamComponents(source, lowercase)
	  }
	}
	
	val analyzerMap = Map(fieldName -> plainAnalyzer, clusterFieldName -> clusterAnalyzer)
	val analyzer = new PerFieldAnalyzerWrapper(plainAnalyzer, analyzerMap.asJava)
	
	val config = new IndexWriterConfig(version, analyzer)
	val iwriter = new IndexWriter(directory, config)
	
	val doc = new Document
	val text = "This is some super text that is super slick."
	
	val fieldType = new FieldType
	fieldType.setIndexed(true)
	fieldType.setStored(true)
	fieldType.setStoreTermVectors(true)
    fieldType.setStoreTermVectorOffsets(true)
	
	doc.add(new Field(clusterFieldName, text, fieldType))
	doc.add(new Field(fieldName, text, fieldType))
	iwriter.addDocument(doc)
	iwriter.commit
	iwriter.close
	
	
	val ireader = DirectoryReader.open(directory)
	val isearcher = new IndexSearcher(ireader)
	
	val x = new RegexQuery(new Term(clusterFieldName, "CLUSTER=00"))
	val y = (new SpanMultiTermQueryWrapper(x).rewrite(ireader)).asInstanceOf[SpanQuery]
	
	
	val spanTerms: Array[SpanQuery] = Array(y, new SpanTermQuery(new Term(clusterFieldName, "slick")))
	val spanQuery = new SpanNearQuery(spanTerms, 0, true)
	
	val arc = ireader.getContext.leaves().get(0)
	val termContexts = new HashMap[Term, TermContext]()
	val bits = new Bits.MatchAllBits(ireader.numDocs())
	
	val spans = spanQuery.getSpans(arc, bits, termContexts)
    while (spans.next) {
      val id = spans.doc
      val start = spans.start()
      val end = spans.end()
      val doc = isearcher.doc(id)
      val tokenStream = TokenSources.getAnyTokenStream(ireader, id, fieldName, analyzer)
      val charTermAttribute = tokenStream.addAttribute(classOf[CharTermAttribute])
      var terms = new ListBuffer[String]
      while (tokenStream.incrementToken) {
        val term = charTermAttribute.toString
        terms += term
      }
      println(terms.slice(start,end) mkString(" "))
	}
	
	
	/*val parser = new QueryParser(version, fieldName, analyzer)
    val query = parser.parse("""super slick""")
    val ireader = DirectoryReader.open(directory)
    val isearcher = new IndexSearcher(ireader)
    val topDocs = isearcher.search(query, null, 1000)
    
    for (scoreDoc <- topDocs.scoreDocs) {
	  val id = scoreDoc.doc
	  val hitDoc = isearcher.doc(id)
	  val tokenStream = TokenSources.getAnyTokenStream(ireader, id, fieldName, analyzer)
	  val charTermAttribute = tokenStream.addAttribute(classOf[CharTermAttribute])
	  val offsetAttribute = tokenStream.addAttribute(classOf[OffsetAttribute])
	  while (tokenStream.incrementToken) {
	    println(offsetAttribute.startOffset + " " + charTermAttribute.toString)
	  }
    }*/
	
	
	
	
  }
  
  
  
  
  
  
  
  
  
      /*val offsetAttribute = tokenStream.addAttribute(classOf[OffsetAttribute])
      val charTermAttribute = tokenStream.addAttribute(classOf[CharTermAttribute])
      tokenStream.reset
      while (tokenStream.incrementToken) {
        println(offsetAttribute.startOffset + " " + charTermAttribute.toString)
      }
	
	
/*	val wordClusters = ("super", "00") :: ("slick", "01") :: Nil  
	val builder = new SynonymMap.Builder(true)
	for {
	  (word, cluster) <- wordClusters
	  wordChar = new CharsRef(word)
	  clusterChar = new CharsRef(cluster)
	} builder.add(wordChar, clusterChar, true)
	val synonymMap = builder.build()
	

	val analyzer = new Analyzer {
	  def createComponents(fieldName: String, reader: Reader): TokenStreamComponents = {
	    val source = new StandardTokenizer(version, reader)
	    val lowercase = new LowerCaseFilter(version, source)
	    val filter = new SynonymFilter(lowercase, synonymMap, true)
	    new TokenStreamComponents(source, filter)
	  }
	}

	val stdAnalyzer = new StandardAnalyzer(version)
	val directory = new NIOFSDirectory(tempDir)
	val config = new IndexWriterConfig(version, analyzer)
	val iwriter = new IndexWriter(directory, config)

	val fieldName = "fieldname"
	val synFieldName = "fieldnameSyn"
	val doc = new Document
	val text = "This is some super text that is super slick."
	  //Field.TermVector.WITH_POSITIONS_OFFSETS
	
	
	
	
	val fieldType = new FieldType
	fieldType.setIndexed(true)
	fieldType.setIndexOptions(FieldInfo.IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
	fieldType.setStored(true)
	fieldType.setStoreTermVectors(true)
    fieldType.setTokenized(true)
    fieldType.setStoreTermVectorOffsets(true)
	
	
	doc.add(new Field(fieldName, text, fieldType))
	doc.add(new Field("foo", text, fieldType, stdAnalyzer))
	//doc.add(new Field(synFieldName, text, fieldType))
	iwriter.addDocument(doc)
	iwriter.close

	val ireader = DirectoryReader.open(directory)
	val isearcher = new IndexSearcher(ireader)

	val parser = new QueryParser(version, fieldName, analyzer)
	val query = parser.parse(""" super slick """)
	
	val spanTerms: Array[SpanQuery] = Array(new SpanTermQuery(new Term(fieldName, "super")), new SpanTermQuery(new Term(fieldName, "slick")))
	val spanQuery = new SpanNearQuery(spanTerms, 0, true)
	
	
	val topDocs = isearcher.search(spanQuery, null, 1000)
	
	val htmlFormatter = new SimpleHTMLFormatter
	
	val matches = new ListBuffer[String]
	val myFormatter = new Formatter {
	  def highlightTerm(originalText: String, tokenGroup: TokenGroup): String = {
	    val x = tokenGroup.getNumTokens
	    if (tokenGroup.getTotalScore > 0) matches += x + " " + originalText
	    originalText
	  }
	}
	
	val arc = ireader.getContext.leaves().get(0)
	
	//val arc = isearcher.getTopReaderContext.asInstanceOf[AtomicReaderContext]
	val termContexts = new HashMap[Term, TermContext]()
	val bits = new Bits.MatchAllBits(ireader.numDocs())
	
	val spans = spanQuery.getSpans(arc, bits, termContexts)
	val window = 0
	while (spans.next) {
      val id = spans.doc
      val start = spans.start()
      val end = spans.end()
      val doc = isearcher.doc(id)
      val tokenStream = TokenSources.getAnyTokenStream(ireader, id, "foo", stdAnalyzer)
      val charTermAttribute = tokenStream.addAttribute(classOf[CharTermAttribute])
      var terms = new ListBuffer[String]
      while (tokenStream.incrementToken) {
        val term = charTermAttribute.toString
        terms += term
      }
      println(terms mkString(" "))
	}
	
	
	/*
	val highlighter = new Highlighter(myFormatter, new QueryScorer(spanQuery))
	
	assert(1 == topDocs.scoreDocs.length)
	for (scoreDoc <- topDocs.scoreDocs) {
	  val id = scoreDoc.doc
	  val hitDoc = isearcher.doc(id)
      assert(text == hitDoc.get(fieldName))
      
      
      
      val tokenStream = TokenSources.getAnyTokenStream(ireader, id, fieldName, stdAnalyzer)
      /*val offsetAttribute = tokenStream.addAttribute(classOf[OffsetAttribute])
      val charTermAttribute = tokenStream.addAttribute(classOf[CharTermAttribute])
      tokenStream.reset
      while (tokenStream.incrementToken) {
        println(offsetAttribute.startOffset + " " + charTermAttribute.toString)
      }
	  tokenStream.reset*/
      
      
      //val frag = highlighter.getBestTextFragments(tokenStream, hitDoc.get(fieldName), false, 4)
      //matches foreach println
      
	}*/
	* 
	* 
	*/
  }*/
  

}