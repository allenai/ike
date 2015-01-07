package org.allenai.dictionary.lucene

import java.io.File
import org.apache.lucene.store.NIOFSDirectory
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field

case class LuceneWriter(path: File) {
  import Lucene._
  val analyzer = new TokenAttributeAnalyzer
  val directory = new NIOFSDirectory(path)
  val config = new IndexWriterConfig(version, analyzer)
  def writer = new IndexWriter(directory, config)
  def makeDoc(text: String): Document = {
    val doc = new Document
    doc.add(new Field(fieldName, text, fieldType))
    println(text)
    doc
  }
  def write(texts: Iterator[String]): Unit = {
    val w = writer
    val docs = texts map makeDoc foreach w.addDocument 
    w.commit
  }
}