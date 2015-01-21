package org.allenai.dictionary

import nl.inl.blacklab.search.Searcher
import java.io.File

case class SimpleApp(indexPath: String) {
  
  val searcher = Searcher.open(new File(indexPath))
  val semantics = BlackLabSemantics(searcher)
  def execute(s: String) = {
    val query = QExprParser.parse(s).get
    val sem = semantics.blackLabQuery(query)
    val hits = searcher.find(sem)
    BlackLabResult.fromHits(hits)
  }
  def executePrint(s: String) = for (result <- execute(s)) {
    val matchWords = result.matchWords mkString " "
    val groups = result.captureGroups
    val groupData = groups.mapValues(i => result.wordData.slice(i.start, i.end))
    val groupStrings = groupData.mapValues(dseq => dseq.map(_.word) mkString " ")
    println(matchWords)
    for ((name, words) <- groupStrings) println(s"$name => $words")
    println
  }

}