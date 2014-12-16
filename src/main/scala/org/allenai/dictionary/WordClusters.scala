package org.allenai.dictionary

import scala.io.Source

object WordClusters {
  def fromTsv(line: String): (String, String) = line.split("\t") match {
    case Array(cluster, word, _) => (word, cluster)
    case _ => throw new IllegalArgumentException(s"Invalid cluster input: $line")
  }
  def fromFile(path: String): Map[String, String] =
    Source.fromFile(path).getLines.map(fromTsv).toMap
}