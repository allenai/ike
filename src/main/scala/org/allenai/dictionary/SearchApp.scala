package org.allenai.dictionary

import com.typesafe.config.ConfigFactory
import java.io.File
import nl.inl.blacklab.search.Searcher

case class SearchRequest(query: String)

object SearchApp {
  val config = ConfigFactory.load
  val indexPath = new File(config.getString("indexPath"))
  val searcher = Searcher.open(indexPath)
  val semantics = BlackLabSemantics(searcher)
  def search(r: SearchRequest): Seq[BlackLabResult] = {
    val query = QExprParser.parse(r.query).get
    val sem = semantics.blackLabQuery(query)
    val hits = searcher.find(sem)
    BlackLabResult.fromHits(hits).toSeq
  }
}
