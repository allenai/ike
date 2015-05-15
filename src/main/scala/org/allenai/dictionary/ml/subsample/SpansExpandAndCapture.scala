package org.allenai.dictionary.ml.subsample

import nl.inl.blacklab.search.Span
import nl.inl.blacklab.search.lucene.{ HitQueryContext, DocFieldLengthGetter, BLSpans }
import nl.inl.blacklab.search.sequences.PerDocumentSortedSpans

class Expansion(
    val spans: BLSpans,
    val captureIndex: Int,
    var offset: Int,
    var more: Boolean
) {

  def ensurePast(start: Int, doc: Int): Boolean = {
    if (more) {
      if (spans.doc < doc) {
        more = spans.skipTo(doc)
      }
      while (more && spans.start < start && spans.doc == doc) {
        more = spans.next()
      }
    }
    more
  }
}

case class SpansExpandAndCapture(
    var clause: BLSpans,
    var expansions: Seq[(String, BLSpans)],
    expansionCaptureGroupName: Option[String],
    ignoreLastToken: Boolean,
    lengthGetter: DocFieldLengthGetter,
    expandToLeft: Boolean
) extends BLSpans {

  if (!clause.hitsStartPointSorted()) {
    clause = new PerDocumentSortedSpans(clause, false, false)
  }

  var more = true
  var aliveExpansions = Seq[Expansion]()
  var initialized = false
  var numExpand = -1
  var docLength = -1
  var docLengthOf = -1
  var expansionCaptureGroupIndex: Option[Int] = None

  def setDocLength(): Unit = {
    if (docLengthOf != clause.doc) {
      docLength = lengthGetter.getFieldLength(clause.doc)
      docLengthOf = clause.doc
      if (ignoreLastToken) {
        docLength -= 1
      }
    }
  }
  override def next(): Boolean = {
    if (more) {
      if (!initialized) {
        initialized = true
        more = clause.next()
        if (more) {
          aliveExpansions.foreach(ex => ex.more = ex.spans.next())
          require(expansions.forall(_._2.hitsAllSameLength()))
          numExpand = expansions.map(_._2).map(_.hitsLength()).sum
          var offset = 0
          aliveExpansions.map { ex =>
            val prevOffset = offset
            offset += ex.spans.hitsLength()
            ex.offset = prevOffset
          }
        }
        more
      } else {
        more = clause.next()
        if (expandToLeft) {
          setDocLength()
          while (more && end < docLength) {
            more = clause.next()
            if (more) setDocLength()
          }
        } else {
          while (more && start < 0) {
            more = clause.next()
          }
        }
        more
      }
    } else {
      false
    }
  }

  override def doc(): Int = clause.doc()

  override def start(): Int = if (expandToLeft) clause.start() - numExpand else clause.start()

  override def end(): Int = if (expandToLeft) clause.end() else clause.end() + numExpand

  override def setHitQueryContext(context: HitQueryContext): Unit = {
    if (expansionCaptureGroupName.isDefined) {
      expansionCaptureGroupIndex =
        Some(context.registerCapturedGroup(expansionCaptureGroupName.get))
    }
    aliveExpansions =
      expansions.map(ex => new Expansion(ex._2, context.registerCapturedGroup(ex._1), -1, true))
  }

  override def passHitQueryContextToClauses(context: HitQueryContext): Unit = {
    clause.setHitQueryContext(context)
  }

  private def syncExpansion(expansion: BLSpans, target: Int): (Boolean, Boolean) = {
    var more = true
    if (expansion.doc < clause.doc) {
      more = expansion.skipTo(clause.doc)
    }
    if (more) {
      if (expandToLeft) {
        while (more && expansion.start() < target && expansion.doc == clause.doc) {
          more = expansion.next()
        }
        (more, more && expansion.start() == target && expansion.doc == clause.doc)
      } else {
        while (more && expansion.end() < target && expansion.doc == clause.doc) {
          more = expansion.next()
        }
        (more, more && expansion.end() == target && expansion.doc == clause.doc)
      }
    } else {
      (false, false)
    }
  }

  override def getCapturedGroups(capturedGroups: Array[Span]): Unit = {
    aliveExpansions.foreach { expansion =>
      val (expansionStart, expansionEnd) =
        if (expandToLeft) {
          (start, expansion.spans.start())
        } else {
          (expansion.spans.end(), end)
        }
      val spansExhausted = expansion.ensurePast(expansionStart, expansionEnd)
      val matched = !spansExhausted && expansion.spans.start == expansionStart &&
        expansion.spans.end == expansionEnd
      val capture = if (matched) {
        new Span(expansionStart, expansionEnd)
      } else {
        new Span(-expansionStart, -expansionEnd)
      }
      capturedGroups.update(expansion.captureIndex, capture)
    }
    if (expansionCaptureGroupIndex.isDefined) {
      if (expandToLeft) {

      } else {

      }
    }
  }
}
