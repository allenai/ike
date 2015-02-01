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
  def captureGroups(hits: Hits, hit: Hit, shift: Int): Map[String, Interval] = {
    val names = hits.getCapturedGroupNames.asScala
    // For some reason BlackLab will sometimes return null values here, so wrap in Options
    val optSpans = hits.getCapturedGroups(hit) map wrapNull
    for {
      (name, optSpan) <- names.zip(optSpans).toMap
      span <- optSpan
      interval = Interval.open(span.start, span.end).shift(-shift)
    } yield (name, interval)
  }
  def fromHit(hits: Hits, hit: Hit, kwicSize: Int = 10): BlackLabResult = {
    val kwic = hits.getKwic(hit, kwicSize)
    val data = wordData(hits, kwic)
    val offset = Interval.open(kwic.getHitStart, kwic.getHitEnd)
    val groups = if (hits.hasCapturedGroups) {
      captureGroups(hits, hit, hit.start - kwic.getHitStart)
    } else {
      Map.empty[String, Interval]
    }
    BlackLabResult(data, offset, groups)
  }
  def fromHits(hits: Hits): Iterator[BlackLabResult] = for {
    hit <- hits.iterator.asScala
    result = fromHit(hits, hit)
  } yield result
  def wrapNull[A](a: A): Option[A] = if (a == null) None else Some(a)
}
