package org.allenai.dictionary

import com.typesafe.config.ConfigFactory
import java.io.File
import nl.inl.blacklab.search.Searcher
import com.typesafe.config.Config
import org.allenai.common.immutable.Interval

case class SearchRequest(query: String, limit: Int = 100, groupBy: Option[String] = None)

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
  def groupedSearch(req: SearchRequest): Seq[GroupedBlackLabResult] = {
    val results = search(req)
    val keyed = results map (keyResult(req, _))
    val grouped = keyed groupBy keyString
    val mapped = grouped map { case (key, results) => GroupedBlackLabResult(key, results) }
    mapped.toSeq
  }
  def keyResult(req: SearchRequest, result: BlackLabResult): KeyedBlackLabResult = {
    val providedInterval = for {
      name <- req.groupBy
      i <- result.captureGroups.get(name)
    } yield i
    val firstInterval = result.captureGroups.values.toSeq.headOption
    val candidateIntervals = Seq(providedInterval, firstInterval, Some(result.matchOffset))
    val keyInterval = candidateIntervals.flatten.head
    KeyedBlackLabResult(keyInterval, result)
  }
  def keyString(kr: KeyedBlackLabResult): String = {
    val i = kr.key
    val wordData = kr.result.wordData.slice(i.start, i.end)
    val words = wordData map (_.word.toLowerCase.trim)
    words mkString " "
  }
}
