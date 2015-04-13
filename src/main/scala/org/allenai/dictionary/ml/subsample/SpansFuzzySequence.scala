package org.allenai.dictionary.ml.subsample

import org.allenai.dictionary.ml.TokenizedQuery

import nl.inl.blacklab.search.Span
import nl.inl.blacklab.search.lucene.{ BLSpans, DocFieldLengthGetter, HitQueryContext, SpansUnique }
import nl.inl.blacklab.search.sequences.PerDocumentSortedSpans

private case class Match(doc: Int, start: Int)

/* Clause in the sequence, marked with its number and its and token within this sequence it matches
 *
 * @param clause underlying BLSpans
 * @param sequenceStart the token number this clause matches in the overall FuzzySequence
 * @param clauseNum number of this clause
 */
private case class SeqClause(clause: BLSpans, sequenceStart: Int,
    clauseNum: Int) extends Ordered[SeqClause] {

  /* If this clause is participating in a sequence, where would that sequence start */
  def matchStart: Int = clause.start - sequenceStart

  override def compare(that: SeqClause): Int = {
    if (that.clause.doc != clause.doc) {
      clause.doc.compare(that.clause.doc)
    } else {
      matchStart.compare(that.matchStart)
    }
  }
}

case class CaptureSpan(name: String, start: Int, end: Int) {
  require(name != null)
  require(start >= 0)
  require(end > start)
}

/** Gathers 'Fuzzy' matches to a sequence. In other words matches sequences within
  * <code>minMatches</code> to <code>maxMatches</code> inclusive edit distance of the input
  * sequence of clauses, where an edit is changing a clause token,
  * or deleting a clause from the start/end of the sequence. Input clauses must be fixed length.
  * additionally supports return subsequences of its matches as capture groups.
  *
  * @param clauses Clauses to of the fuzzy sequence, should be fixed length
  * @param documentLengthGetter Getter to find the lengths of documents
  * @param minMatches minimum number of clauses to participate in each match
  * @param maxMatches maximum number of clauses to participate in each match
  * @param ignoreLastToken whether the the last token of sentences should be skipped
  * @param sequencesToCapture Subsequences to return as capture groups
  * @param registerMisses Whether to return clauses that missed a match as spans
  *                  indicating where the missed clause 'should' have been placed
  */
class SpansFuzzySequence(
    private val clauses: Seq[Either[BLSpans, Int]],
    private val documentLengthGetter: DocFieldLengthGetter,
    private var minMatches: Int,
    private var maxMatches: Int,
    private val allowEndDeletion: Boolean,
    private val ignoreLastToken: Boolean,
    private val sequencesToCapture: Seq[CaptureSpan],
    private val registerMisses: Boolean
) extends BLSpans {

  def this(
    clauses: => Seq[BLSpans], // use "=>" to avoid conflicting signature w/previous constructor
    documentLengthGetter: DocFieldLengthGetter,
    minMatches: Int,
    maxMatches: Int,
    allowEndDeletion: Boolean,
    ignoreLastToken: Boolean,
    sequencesToCapture: Seq[CaptureSpan],
    registerMisses: Boolean
  ) = {
    this(clauses map (Left(_)), documentLengthGetter, minMatches, maxMatches, allowEndDeletion,
      ignoreLastToken, sequencesToCapture, registerMisses)
  }

  def this(
    clauses: Seq[BLSpans],
    documentEnds: DocFieldLengthGetter,
    minMatches: Int,
    ignoreLastToken: Boolean,
    sequencesToCapture: Seq[CaptureSpan] = Seq()
  ) = {
    this(clauses, documentEnds, minMatches, -1, false,
      ignoreLastToken, sequencesToCapture, false)
  }

  // Until isInitialized() is called we cannot reliably know the length
  // of child clauses, so the rest of our validation is deferred until then.
  require(minMatches > 0)

  var maxOutOfBoundsLeft = 0
  var maxOutOfBoundsRight = 0
  var docLength = -1

  /* Length of the sequence we will match to */
  var hitLength = -1

  /* Spans object we are using */
  private val realClauses = clauses flatMap {
    case Left(c) => Some(c)
    case _ => None
  }

  /* Number of matches that implicitly match everything */
  private var numImplicitMatches = -1

  private var implicitMatchStarts = Seq[Int]()
  private var implicitMatchEnds = Seq[Int]()

  /* Clauses that are candidates for participating in a fuzzy sequence match. Never
   * contains clauses that are empty. */
  private var aliveClauses = Seq[SeqClause]()

  /* Clauses that are empty */
  private var deadClauses = Seq[SeqClause]()

  /* The match this is currently on */
  private var currentMatch = Match(-1, -1)

  /* Whether there are any more matches to return */
  private var more = true

  /* Whether this has been initialized */
  private var initialized = false

  /* What indices in our QueryRequestContext our capture spans correspond to */
  private var captureIndices = Seq[Int]()

  /* If we are going to capture misses, the indices of each clauses's misses should be
     set to in our QueryRequestContext*/
  private var missedClauseIndices = IndexedSeq[Int]()

  override def next(): Boolean = {
    if (!more) return false
    more =
      if (!initialized) {
        initialize() && moveToValidMatch()
      } else {
        advance() && moveToValidMatch()
      }
    more
  }

  override def skipTo(target: Int): Boolean = {
    if (more) {
      more = if (!initialized) {
        initialize() && {
          if (aliveClauses.forall(_.clause.doc >= target)) {
            moveToValidMatch()
          } else {
            skipTo(target)
          }
        }
      } else {
        // Have all our clauses that need to skip ahead skip, filter
        // any that became empty
        aliveClauses = aliveClauses.filter(sc => {
          if (sc.clause.doc() < target) {
            val hasNext = sc.clause.skipTo(target)
            if (!hasNext) deadClauses = sc +: deadClauses
            hasNext
          } else {
            true
          }
        })
        if (aliveClauses.size + numImplicitMatches < minMatches) {
          // Not enough clauses could advance to target
          false
        } else {
          val newMin = Match(aliveClauses.min.clause.doc, aliveClauses.min.matchStart)
          // If skipTo has not changed any clauses, we need to advance to ensure
          // we return a new match. Then move to the next valid match
          if (newMin == currentMatch) advance() else setCurrentMatch()
          moveToValidMatch()
        }
      }
      more
    } else {
      false
    }
  }

  /* Initialize this by calling next() and assigning aliveClauses, deadClauses, currentMatch
   * and hitLength to initial values. Does NOT make this is on a valid match.
   */
  private def initialize(): Boolean = {
    initialized = true
    val hasNext = realClauses map (_.next())

    def seqToLength(seq: Either[BLSpans, Int]): Int = seq match {
      case Left(spans) => spans.hitsLength()
      case Right(size) => size
    }

    // We have to do this check, along with the some other
    // initializations, here not at the constructor,
    // because at least one BlackLab Span (see BLSpanOrQuery)
    // does not return hitsAllSameLength correctly until next() has been called
    assert(realClauses.forall(_.hitsAllSameLength()))

    var onIndex = 0
    val startIndices = clauses.map(x => {
      val curIndex = onIndex
      onIndex += seqToLength(x)
      curIndex
    })

    val (iStarts, iEnds) = startIndices.zip(clauses)
      .flatMap {
        case (start, Right(size)) => Some((start, start + size))
        case _ => None
      }.unzip
    implicitMatchStarts = iStarts
    implicitMatchEnds = iEnds
    numImplicitMatches = implicitMatchStarts.size

    hitLength = onIndex

    assert(sequencesToCapture.forall(_.end <= hitLength))

    if (maxMatches == -1) maxMatches = hitLength

    val sequencedClauses = clauses.zipWithIndex.flatMap {
      case (clause, index) =>
        clause match {
          case Right(n) => None
          case Left(spans) =>
            val wrappedSpans =
              if (!spans.hitsStartPointSorted()) {
                new PerDocumentSortedSpans(spans, false, !spans.hitsAreUnique())
              } else if (!spans.hitsAreUnique()) {
                new SpansUnique(spans)
              } else {
                spans
              }
            Some(SeqClause(wrappedSpans, startIndices(index), index))
        }
    }

    aliveClauses = sequencedClauses zip hasNext filter (_._2) map (_._1)
    deadClauses = sequencedClauses zip hasNext filterNot (_._2) map (_._1)

    if (allowEndDeletion) {
      val leftMax = clauses.drop(minMatches).map(seqToLength).sum
      val rightMax = clauses.dropRight(minMatches).map(seqToLength).sum
      if (sequencesToCapture.isEmpty) {
        maxOutOfBoundsLeft = leftMax
        maxOutOfBoundsRight = rightMax
      } else {
        val leftCapture = sequencesToCapture.map(_.start).min
        val rightCapture = hitLength - sequencesToCapture.map(_.end).max
        maxOutOfBoundsLeft = math.min(leftCapture, leftMax)
        maxOutOfBoundsRight = math.min(rightCapture, rightMax)
      }
    }

    if (aliveClauses.size + numImplicitMatches < minMatches) {
      false
    } else {
      setCurrentMatch()
      true
    }
  }

  /* Set currentMatch depending on the state of aliveClauses. Should be called whenever
   * a clause from aliveClause is advanced.
   */
  private def setCurrentMatch(): Unit = {
    val minClause = aliveClauses.min
    currentMatch = Match(minClause.clause.doc, minClause.matchStart)
  }

  /* Advance all clauses that participated in the currentMatch, does NOT ensure this is
   * at a valid match. Returns false is we could not advance.
   */
  private def advance(): Boolean = {
    aliveClauses = aliveClauses.filter(sc => {
      if (sc.clause.doc == currentMatch.doc && sc.matchStart == currentMatch.start) {
        val hasNext = sc.clause.next()
        if (!hasNext) deadClauses = sc +: deadClauses
        hasNext
      } else {
        true
      }
    })
    if (aliveClauses.size + numImplicitMatches < minMatches) {
      false
    } else { setCurrentMatch(); true }
  }

  /* Ensures this is on a valid match, advancing clauses if needed.
   * Returns true there are no more valid matches ot get.
   */
  private def moveToValidMatch(): Boolean = {
    while (!isValidMatch) {
      if (!advance()) return false
    }
    true
  }

  /* Returns true if and only if this is on a valid match
   */
  private def isValidMatch: Boolean = {
    // Check document bounds
    val tokenPad = if (ignoreLastToken) 1 else 0
    docLength = documentLengthGetter.getFieldLength(currentMatch.doc) - tokenPad
    if ((currentMatch.start + maxOutOfBoundsLeft) < 0 ||
      sequenceEnd > (docLength + maxOutOfBoundsRight)) {
      false
    } else {
      val numMatches =
        if (currentMatch.start < 0 || currentMatch.start + hitLength > docLength) {
          // Need to check for out of bounds matches
          implicitMatchStarts.count(x =>
            x + currentMatch.start >= 0 && x + currentMatch.start < docLength) +
            aliveClauses.count {
              case sc =>
                sc.clause.doc == doc &&
                  sc.matchStart == currentMatch.start &&
                  sc.clause.end <= docLength &&
                  sc.clause.start >= 0
            }
        } else {
          numImplicitMatches + aliveClauses.count {
            case sc => sc.clause.doc == doc && sc.matchStart == currentMatch.start
          }
        }
      numMatches >= minMatches && numMatches <= maxMatches
    }
  }

  override def passHitQueryContextToClauses(context: HitQueryContext): Unit = {
    realClauses.foreach(x => x.setHitQueryContext(context))
  }

  override def setHitQueryContext(context: HitQueryContext): Unit = {
    super.setHitQueryContext(context)
    captureIndices = sequencesToCapture.map(x => context.registerCapturedGroup(x.name))
    if (registerMisses) {
      val missedClauseNames = TokenizedQuery.getTokenNames(clauses.size)
      missedClauseIndices = missedClauseNames.zip(clauses).map {
        case (name, clause) => clause match {
          case Left(_) => context.registerCapturedGroup(name)
          case Right(_) => -1
        }
      }.toIndexedSeq
    }
  }

  override def getCapturedGroups(capturedGroups: Array[Span]): Unit = {
    // Fill capturedGroups with the subsequences this is capturing
    captureIndices.zip(sequencesToCapture) foreach {
      case (index, CaptureSpan(_, cStart, cEnd)) =>
        capturedGroups.update(index, new Span(sequenceStart + cStart, sequenceStart + cEnd))
    }

    if (registerMisses) {
      // Fill with any clauses that did not participate in the current match
      aliveClauses foreach { clause =>
        val missedStart = sequenceStart + clause.sequenceStart
        val missedEnd = missedStart + clause.clause.hitsLength()
        capturedGroups(missedClauseIndices(clause.clauseNum)) =
          if (missedStart < 0 || missedEnd > docLength) {
            // Clause needs to be deleted to get this match
            new Span(-1, -1)
          } else if (clause.matchStart != sequenceStart || clause.clause.doc != doc) {
            // Clause needed was not in the match
            val missedStart = sequenceStart + clause.sequenceStart
            val missedEnd = missedStart + clause.clause.hitsLength()
            new Span(-missedStart, -missedEnd)
          } else {
            // Clause was in the match
            new Span(clause.clause.start, clause.clause.end)
          }
      }
      // Fill with dead clauses, which also cannot have been in the current match
      deadClauses.foreach(clause => {
        val missedStart = sequenceStart + clause.sequenceStart
        val missedEnd = missedStart + clause.clause.hitsLength()
        capturedGroups(missedClauseIndices(clause.clauseNum)) =
          if (missedStart >= 0 && missedEnd <= docLength) {
            new Span(-missedStart, -missedEnd)
          } else {
            new Span(-1, -1)
          }
      })
    }

    // Get the other capture groups from our clauses
    if (childClausesCaptureGroups) realClauses.foreach(_.getCapturedGroups(capturedGroups))
  }

  override def hitsLength(): Int = hitLength

  override def doc(): Int = currentMatch.doc

  def sequenceStart(): Int = currentMatch.start

  def sequenceEnd(): Int = sequenceStart + hitsLength

  override def start(): Int = math.max(currentMatch.start, 0)

  override def end(): Int = math.min(
    sequenceStart + hitsLength,
    documentLengthGetter.getFieldLength(doc) - (if (ignoreLastToken) 1 else 0)
  )

}
