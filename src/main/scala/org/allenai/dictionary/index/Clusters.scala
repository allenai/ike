package org.allenai.dictionary.index
import org.allenai.pipeline.IoHelpers._
import java.io.File
import scala.io.Source

case class ClusterRecord(cluster: String, word: String, count: Int)

object Clusters {
  val format = columnFormat3(ClusterRecord.apply, '\t')
  def fromFile(f: File): Map[String, String] = {
    val lines = Source.fromFile(f).getLines
    val records = lines map format.fromString
    val pairs = records map { r => (r.word -> r.cluster) }
    pairs.toMap
  }
}
