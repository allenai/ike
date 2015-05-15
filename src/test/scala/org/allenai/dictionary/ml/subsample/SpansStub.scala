package org.allenai.dictionary.ml.subsample

import java.util

import nl.inl.blacklab.search.Span
import nl.inl.blacklab.search.lucene.{ BLSpans, HitQueryContext }

object SpansStub {
  def apply(docs: Seq[Int], s: Seq[Int], e: Seq[Int]): SpansStub =
    new SpansStub(docs.toIndexedSeq, s.toIndexedSeq, e.toIndexedSeq, Seq(), IndexedSeq())

  def apply(data: (Seq[Int], Seq[Int], Seq[Int])): SpansStub = apply(data._1, data._2, data._3)

  def apply(data: Seq[(Int, Int, Int)]): SpansStub = apply(data.unzip3)

  def apply(data: Seq[(Int, Int)], length: Int): SpansStub =
    apply(data.map((x => (x._1, x._2, x._2 + length))))

  def withCaptures(
      data: Seq[(Int, Int, Int)], captures: Seq[Seq[Span]], names: Seq[String]): SpansStub = {
    val (d, s, e) = data.unzip3
    new SpansStub(d.toIndexedSeq, s.toIndexedSeq, e.toIndexedSeq, names, captures.toIndexedSeq)
  }
}
/** Stub Spans class for testing
  */
class SpansStub(
    val docs: IndexedSeq[Int],
    val starts: IndexedSeq[Int],
    val ends: IndexedSeq[Int],
    val captureNames: Seq[String],
    val captures: IndexedSeq[Seq[Span]]
) extends BLSpans {

  private var current = -1
  private var captureNumbers = Seq[Int]()

  def expected(index: Int): (Int, Int, Int) = {
    (docs(index), starts(index), ends(index))
  }

  override def hitsLength(): Int = {
    if (docs.size > 0) {
      val differences = starts.zip(ends).map { case (start, end) => end - start }
      if (differences.forall(_ == differences.head)) differences.head else -1
    } else {
      // Force all length zero spans to be of length one for the moment
      1
    }
  }


  override def setHitQueryContext(context: HitQueryContext): Unit = {
    captureNumbers = captureNames.map(context.registerCapturedGroup(_))
  }

  override def passHitQueryContextToClauses(context: HitQueryContext): Unit = {}

  override def getCapturedGroups(capturedGroups: Array[Span]): Unit = {
    val onCaptures = captures(current)
    captureNumbers.zip(onCaptures).foreach {
      case (i, span) => capturedGroups.update(i, span)
    }
  }

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
