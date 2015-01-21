package org.allenai.dictionary

import java.io.File
import nl.inl.blacklab.index.Indexer
import java.io.StringReader
import nl.inl.blacklab.search.Searcher

object TestData {
  
  val docSents = Map(
    "doc1" -> Seq("I like mango .", "It tastes great ."),
    "doc2" -> Seq("I hate those bananas .", "They taste not great ."))
  
  val posTags = Map(
      "I" -> "PRP",
      "like" -> "VBP",
      "mango" -> "NN",
      "." -> ".",
      "It" -> "PRP",
      "tastes" -> "VBP",
      "great" -> "JJ",
      "hate" -> "VBP",
      "those" -> "DT",
      "bananas" -> "NNS",
      "They" -> "PRP",
      "taste" -> "VBP",
      "not" -> "RB",
      "great" -> "JJ")
  
  val clusters = Map(
      "I" -> "01",
      "like" -> "10",
      "mango" -> "00",
      "." -> "11",
      "It" -> "01",
      "tastes" -> "10",
      "great" -> "11",
      "hate" -> "10",
      "those" -> "11",
      "bananas" -> "00",
      "They" -> "01",
      "taste" -> "10",
      "not" -> "11",
      "great" -> "11")
  
  def wordData(word: String): WordData = {
    val pos = posTags.get(word).map(p => ("pos" -> p))
    val cluster = clusters.get(word).map(c => ("cluster" -> c))
    val attrs = Seq(pos, cluster).flatten.toMap
    WordData(word, attrs)
  }
  
  def sentence(s: String): Seq[WordData] = s.split(" ").map(wordData)
  
  val documents = for {
    (name, sentStrings) <- docSents
    sents = sentStrings map sentence
    doc = BlackLabDocument(name, sents)
  } yield doc
  
  def createTestIndex(path: File): Unit = {
    val indexer = new Indexer(path, true, classOf[AnnotationIndexer])
    for (d <- documents) {
      val xml = XmlSerialization.toXml(d)
      val name = d.name
      indexer.index(name, new StringReader(xml.toString))
    }
    indexer.close
  }
  
  def testSearcher(path: File): Searcher = Searcher.open(path)

}