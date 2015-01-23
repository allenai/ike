package org.allenai.dictionary

import com.typesafe.config.ConfigFactory
import java.io.File
import nl.inl.blacklab.search.Searcher

case class SearchRequest(query: String, limit: Int = 100)

object SearchApp {
  val config = ConfigFactory.load
  val indexPath = new File(config.getString("indexPath"))
  val searcher = Searcher.open(indexPath)
  def search(r: SearchRequest): Seq[BlackLabResult] = {
    val query = QExprParser.parse(r.query).get
    val sem = BlackLabSemantics.blackLabQuery(query)
    val hits = searcher.find(sem).window(0, r.limit)
    BlackLabResult.fromHits(hits).toSeq
  }
}
