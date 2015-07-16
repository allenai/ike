package org.allenai.dictionary

import org.allenai.common.{ Timing, Logging }
import org.allenai.dictionary.persistence.Tablestore

object SearchResultGrouper extends Logging {
  def targetTable(req: SearchRequest): Option[Table] = for {
    target <- req.target
    table <- Tablestore.tables(req.userEmail).get(target)
  } yield table

  /** Attempts to map the capture group names in the result to the target table's column names
    * in the request. If unable to do so, returns the result unchanged.
    */
  def inferCaptureGroupNames(req: SearchRequest, result: BlackLabResult): BlackLabResult = {
    // If there are no capture groups, use the entire match string as a capture group
    val groups = result.captureGroups match {
      case groups if groups.isEmpty => Map("match" -> result.matchOffset)
      case groups => groups
    }
    val groupNames = groups.keys.toList.sortBy(groups)
    val updatedGroups = for {
      table <- targetTable(req)
      cols = table.cols
      if cols.size == groupNames.size
      if cols.toSet != groupNames.toSet
      nameMap = groupNames.zip(cols).toMap
      updatedGroups = groups.map { case (name, interval) => (nameMap(name), interval) }
    } yield updatedGroups
    val newGroups = updatedGroups.getOrElse(groups)
    result.copy(captureGroups = newGroups)
  }

  /** Tokenized results for a single column, for example ["information", "extraction"]
    */
  type Phrase = Seq[String]

  def keyResult(req: SearchRequest, result: BlackLabResult): KeyedBlackLabResult = {
    val groups = result.captureGroups
    val columns = targetTable(req) match {
      case Some(table) => table.cols
      case None => throw new IllegalArgumentException(s"No target table found")
    }
    val intervals = for {
      col <- columns
    } yield groups.getOrElse(
      col,
      throw new IllegalArgumentException(s"Could not find column $col in results")
    )
    KeyedBlackLabResult(intervals, result)
  }

  def createGroups(
    req: SearchRequest,
    keyed: Iterable[KeyedBlackLabResult]
  ): Seq[GroupedBlackLabResult] = {
    def keyString(kr: KeyedBlackLabResult): Seq[Phrase] = kr.keys.map { interval =>
      kr.result.wordData.slice(interval.start, interval.end).map(_.word.toLowerCase.trim)
    }
    val grouped = keyed groupBy keyString

    /** Checks if subsetKey is a subset of supersetKey.
      *
      * ["sky", "blue and white"] is a subset of
      * ["big sky", "blue and white during the day"]. A key is always a subset of
      * itself. Column order matters, so ["a", "b"] is not a subset of
      * ["b", "a"].
      */
    def isSubset(subsetKey: Seq[Phrase], supersetKey: Seq[Phrase]): Boolean = {
      require(subsetKey.length == supersetKey.length)
      (subsetKey zip supersetKey).forall {
        case (subsetColumn, supersetColumn) =>
          supersetColumn.containsSlice(subsetColumn)
      }
    }

    Timing.timeThen {
      grouped.map {
        case (keyString, group) =>
          val keys = keyString.map(_.mkString(" "))
          val groupSubset = group.take(req.config.evidenceLimit)
          val relevanceScore = grouped.map {
            case (innerKeyString, innerGroup) =>
              if (isSubset(innerKeyString, keyString)) innerGroup.size else 0
          }.sum

          GroupedBlackLabResult(
            keys,
            group.size,
            relevanceScore,
            groupSubset
          )
      }.toSeq
    } { duration =>
      val seconds = duration.toSeconds
      if (seconds > 1) logger.info(s"Scoring results took ${seconds}s")
    }
  }

  /** Groups the given results. The groups are keyed using the match groups corresponding to the
    * target table's columns.
    */
  def groupResults(req: SearchRequest, results: Iterable[BlackLabResult]): Seq[GroupedBlackLabResult] = {
    val withColumnNames = results.map(inferCaptureGroupNames(req, _))
    val keyed = withColumnNames.map(keyResult(req, _))
    createGroups(req, keyed)
  }

  /** Groups each result into its own group. Useful when there is no target dictionary defined.
    */
  def identityGroupResults(
    req: SearchRequest,
    results: Iterable[BlackLabResult]
  ): Seq[GroupedBlackLabResult] = {
    val keyed = results map { r => KeyedBlackLabResult(r.matchOffset :: Nil, r) }
    createGroups(req, keyed)
  }
}
