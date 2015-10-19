package org.allenai.dictionary

import org.allenai.common.immutable.Interval
import org.allenai.common.{ Logging, Timing }

import scala.collection.immutable

object SearchResultGrouper extends Logging {
  def targetTable(req: SearchRequest, tables: Map[String, Table]): Option[Table] = for {
    target <- req.target
    table <- tables.get(target)
  } yield table

  /** Attempts to map the capture group names in the result to the target table's column names
    * in the request.
    * If unable to map capture group names, returns the result unchanged.
    * The result is an option because it returns None in cases where the BlackLabResult coming in
    * has to be filtered because it didn't match certain query criteria, in particular, if differnt
    * parts of the result matching a certain table's different columns have to match in the row they
    * occur in but do not.
    */
  def inferCaptureGroupNames(
    req: SearchRequest,
    tables: Map[String, Table],
    result: BlackLabResult
  ): Option[BlackLabResult] = {

    // Helper Function that takes a TableRow and a collection of tuples containing column indices
    // and corresponding expected matches and checks to see if the TableRow has the expected matches
    // in the specified columns.
    def hasMatchesInColumns(row: TableRow, expectedColumnMatches: Seq[(Int, Seq[WordData])]): Boolean = {
      !expectedColumnMatches.find(m =>
        !row.values(m._1).qwords.map(_.value).mkString(" ").equalsIgnoreCase(
          m._2.map(_.word).mkString(" ")
        )).isDefined
    }

    // Helper Function that takes a collection of tuples containing associated with the same table in
    // the form: (Table Capture Group, tableName, groupName, tag) and checks to see if their matches
    // come from the same table row.
    def containsMatchesFromDifferentRows(
      tableName: String,
      associatedTableCaptureGroups: Seq[((String, Interval), String, String, Int)]
    ): Boolean = {
      // Return true if a tuple is found that has a different than expected table name.
      if (associatedTableCaptureGroups.find(x => !x._2.equals(tableName)).isDefined) {
        true
      } else {
        tables.get(tableName) match {
          case Some(table) =>
            // Construct tuples with column indexes and corresponding matched strings to
            // check if the specified table has these matched strings in the respective column
            // indices IN THE SAME ROW.
            val columnMatches = for {
              g <- associatedTableCaptureGroups
            } yield {
              val interval = g._1._2
              val matchedWords = result.wordData.slice(interval.start, interval.end)
              val columnName = g._3
              val columnIndex =
                table.cols.zipWithIndex.find(c => c._1.equalsIgnoreCase(columnName)).map(_._2)
              if (!columnIndex.isDefined) {
                throw new IllegalArgumentException(s"Could not find column $columnName in table $tableName")
              }
              (columnIndex.get, matchedWords)
            }
            // Get table rows to consider and check if there exists a row with the strings in
            // corresponding columns.
            val tableRows = table.positive
            tableRows.find(row => hasMatchesInColumns(row, columnMatches)).isDefined
          case None => throw new IllegalArgumentException(s"Could not find table $tableName")
        }
      }
    }

    // If there are no capture groups, use the entire match string as a capture group
    val groups = result.captureGroups match {
      case groups if groups.isEmpty => Map("match" -> result.matchOffset)
      case groups => groups
    }

    // If the BlackLabResult contains matches from Table Capture Groups, verify that the matches
    // come from the same table row, if not reject the BlackLabResult right here.
    val tableGroups = groups.filter(
      gp1 => gp1._1.startsWith(QExprParser.tableCaptureGroupPrefix)
    )
    val tableColTagCaptureGroupRegex = s"""${QExprParser.tableCaptureGroupPrefix} (\w+)_(\w+)_(\d+)""".r

    // Collect all the (tableName, groupName, tag) tuples for the Table Capture Groups.
    val groupInfos = (for {
      tableGroup <- tableGroups
      tableColTagMatch <- tableColTagCaptureGroupRegex.findFirstMatchIn(tableGroup._1)
      if (tableColTagMatch.groupCount == 3)
    } yield {
      (tableGroup, tableColTagMatch.group(1), tableColTagMatch.group(2), tableColTagMatch.group(3).toInt)
    }).toSeq

    // Group together Table Capture Groups to be row-wise associated with each other.
    // These are the ones that have the same table name and the same integer tag.
    val rowWiseAssociatedGroups = groupInfos.groupBy(i => (i._2, i._4))

    // For each set of Table Capture Groups, check that the matching text from different columns come
    // from the same row in the table.
    if (rowWiseAssociatedGroups.find((k, v) => containsMatchesFromDifferentRows(k._1, v))) {
      None
    } else {
      // Get group names. Filter Capture Groups that wrap the special Table Capture Groups
      // and basically match the same tokens.
      val groupNames = groups.filter(
        gp1 => gp1._1.startsWith(BlackLabSemantics.genericCaptureGroupNamePrefix) &&
          groups.find(gp2 => (gp2._1.startsWith(QExprParser.tableCaptureGroupPrefix))
            && (gp2._2.equals(gp1._2))).isDefined
      ).keys.toList.sortBy(groups)

      val updatedGroups = for {
        table <- targetTable(req, tables)
        cols = table.cols
        if cols.size == groupNames.size
        if cols.toSet != groupNames.toSet
        nameMap = groupNames.zip(cols).toMap
        updatedGroups = groups.map { case (name, interval) => (nameMap(name), interval) }
      } yield updatedGroups
      val newGroups = updatedGroups.getOrElse(groups)
      Some(result.copy(captureGroups = newGroups))
    }
  }

  /** Tokenized results for a single column, for example ["information", "extraction"]
    */
  type Phrase = Seq[String]

  def keyResult(
    req: SearchRequest,
    tables: Map[String, Table],
    result: BlackLabResult
  ): KeyedBlackLabResult = {
    val groups = result.captureGroups
    val columns = targetTable(req, tables) match {
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
  def groupResults(
    req: SearchRequest,
    tables: Map[String, Table],
    results: Iterable[BlackLabResult]
  ): Seq[GroupedBlackLabResult] = {
    val withColumnNames = results.map(inferCaptureGroupNames(req, tables, _))
    val keyed = withColumnNames.flatMap(keyResult(req, tables, _))
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
