package org.allenai.dictionary

import scala.collection.JavaConverters._
import org.allenai.common.immutable.Interval
import nl.inl.blacklab.search.Hit
import nl.inl.blacklab.search.Hits
import nl.inl.blacklab.search.Kwic

case class BlackLabResult(wordData: Seq[WordData], matchOffset: Interval,
    captureGroups: Map[String, Interval]) {
  def matchData: Seq[WordData] = wordData.slice(matchOffset.start, matchOffset.end)
  def matchWords: Seq[String] = matchData.map(_.word)
}
    
case object BlackLabResult {
  def wordData(hits: Hits, kwic: Kwic): Seq[WordData] = {
    val attrNames = kwic.getProperties.asScala
    val attrValues = kwic.getTokens.asScala.grouped(attrNames.size)
    val attrGroups = attrValues.map(attrNames.zip(_).toMap).toSeq
    for {
      attrGroup <- attrGroups
      word = attrGroup.get("word") match {
        case Some(value) => value
        case _ => throw new IllegalStateException(s"kwic $kwic does not have 'word' attribute")
      }
      data = WordData(word, attrGroup - "word")
    } yield data
  }
  def captureGroups(hits: Hits, hit: Hit): Map[String, Interval] = if (hits.hasCapturedGroups) {
    val names = hits.getCapturedGroupNames
    val spanMap = hits.getCapturedGroupMap(hit).asScala.toMap
    for {
      (name, span) <- spanMap
      offset = Interval.open(span.start, span.end)
    } yield (name, offset)
  } else Map.empty
  def fromHit(hits: Hits, hit: Hit, kwicSize: Int = 5): BlackLabResult = {
    val kwic = hits.getKwic(hit, kwicSize)
    val data = wordData(hits, kwic)
    val offset = Interval.open(hit.start, hit.end)
    val groups = captureGroups(hits, hit)
    BlackLabResult(data, offset, groups)
  }
  def fromHits(hits: Hits): Iterator[BlackLabResult] = for {
    hit <- hits.iterator.asScala
    result = fromHit(hits, hit)
  } yield result
}