package org.allenai.dictionary.ml.subsample

import java.util

import nl.inl.blacklab.search.Span
import nl.inl.blacklab.search.lucene.{ BLSpans, HitQueryContext }

/** Stub Spans class for testing
 */
case class SpansStub(
  private val docs: IndexedSeq[Int],
    private val starts: IndexedSeq[Int],
    private val ends: IndexedSeq[Int]
) extends BLSpans {

  def this(docs: Seq[Int], s: Seq[Int], e: Seq[Int]) =
    this(docs.toIndexedSeq, s.toIndexedSeq, e.toIndexedSeq)

  def this(data: (Seq[Int], Seq[Int], Seq[Int])) = this(data._1, data._2, data._3)

  def this(data: Seq[(Int, Int, Int)]) = this(data.unzip3)

  def this(data: Seq[(Int, Int)], length: Int) = this(data.map((x => (x._1, x._2, x._2 + length))))

  private var current = -1

  override def hitsLength() = {
    if (docs.size > 0) {
      val differences = starts.zip(ends).map { case (start, end) => end - start }
      if (differences.forall(_ == differences.head)) differences.head else -1
    } else {
      // Force all length zero spans to be of length one for the moment
      1
    }
  }

  override def passHitQueryContextToClauses(context: HitQueryContext): Unit = {}

  override def getCapturedGroups(capturedGroups: Array[Span]): Unit = {}

  override def doc(): Int = docs(current)

  override def end(): Int = ends(current)

  override def start(): Int = starts(current)

  override def next(): Boolean = {
    // Deliberately allow this to get in a bad state if next() is called when the previous
    // call to next() was false since (to my knowledge) this matches Spans API
    current += 1
    current < docs.length
  }

  override def skipTo(target: Int): Boolean = {
    var more = true
    while (more && (current < 0 || doc() < target))
      more = next()
    more
  }

  override def getPayload: util.Collection[Array[Byte]] = null

  override def isPayloadAvailable: Boolean = false
}
