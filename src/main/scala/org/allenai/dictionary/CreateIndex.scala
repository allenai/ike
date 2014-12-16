package org.allenai.dictionary

import scala.io.Source
import java.io.File

object CreateIndex extends App {
  val documentPath = args(0)
  val clusterPath = args(1)
  val indexPath = args(2)
  val documentLines = Source.fromFile(documentPath).getLines
  val documents = for {
    (line, i) <- documentLines.zipWithIndex
    _ = if (i % 500 == 0) println(s"At document $i") else 0
  } yield line
  val clusters = WordClusters.fromFile(clusterPath)
  val writer = LuceneWriter(new File(indexPath), clusters)
  writer.write(documents)
}