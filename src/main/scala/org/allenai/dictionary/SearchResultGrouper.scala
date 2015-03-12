package org.allenai.dictionary

object SearchResultGrouper {
  def targetTable(req: SearchRequest): Option[Table] = for {
    target <- req.target
    table <- req.tables.get(target)
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
  /** Constructs the string key used to join multiple result objects.
    */
  def keyString(kr: KeyedBlackLabResult): String = {
    val values = for {
      interval <- kr.keys
      wordData = kr.result.wordData.slice(interval.start, interval.end)
      words = wordData.map(_.word.toLowerCase.trim)
      value = words mkString " "
    } yield value
    "(" + values.mkString(", ") + ")"
  }
  def keyResult(req: SearchRequest, result: BlackLabResult): KeyedBlackLabResult = {
    val groups = result.captureGroups
    val groupNames = groups.keys
    val columns = targetTable(req) match {
      case Some(table) => table.cols
      case None => throw new IllegalArgumentException(s"No target table found")
    }
    val intervals = for {
      col <- columns
      interval = groups.get(col) match {
        case Some(interval) => interval
        case None => throw new IllegalArgumentException(s"Could not find column $col in results")
      }
    } yield interval
    KeyedBlackLabResult(intervals, result)
  }
  /** Groups the given results. The groups are keyed using the match groups corresponding to the
    * target table's columns.
    */
  def groupResults(req: SearchRequest, results: Seq[BlackLabResult]): Seq[GroupedBlackLabResult] = {
    val withColumnNames = results.map(inferCaptureGroupNames(req, _))
    val keyed = withColumnNames.map(keyResult(req, _))
    val grouped = keyed groupBy keyString map {
      case (keyString, group) => GroupedBlackLabResult(keyString, group.size, group)
    }
    grouped.toSeq
  }
}
