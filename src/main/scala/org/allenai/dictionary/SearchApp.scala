package org.allenai.dictionary

import com.typesafe.config.ConfigFactory
import java.io.File
import nl.inl.blacklab.search.Searcher
import com.typesafe.config.Config

case class SearchRequest(query: String, limit: Int = 100)

case class SearchApp(config: Config) {
  val indexDir = config.getString("location") match {
    case "file" => new File(config.getString("path"))
    case "datastore" => 
      val ref = DatastoreRef.fromConfig(config.getConfig("item"))
      ref.directoryPath.toFile
    case _ => throw new IllegalArgumentException(s"'location' must be either 'file' or 'datastore")
  }
  val searcher = Searcher.open(indexDir)
  def search(r: SearchRequest): Seq[BlackLabResult] = {
    val query = QExprParser.parse(r.query).get
    val sem = BlackLabSemantics.blackLabQuery(query)
    val hits = searcher.find(sem).window(0, r.limit)
    BlackLabResult.fromHits(hits).toSeq
  }
}
