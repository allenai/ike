package org.allenai.dictionary.lucene

import java.io.File
import org.apache.lucene.store.NIOFSDirectory
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field

case class LuceneWriter(path: File) {
  import Lucene._
  val directory = new NIOFSDirectory(path)
  val config = new IndexWriterConfig(version, analyzer)
  def writer = new IndexWriter(directory, config)
  def write(texts: Iterator[IndexableSentence]): Unit = {
    val w = writer
    val docs = texts map IndexableSentence.toLuceneDoc foreach w.addDocument 
    w.commit
  }
}
