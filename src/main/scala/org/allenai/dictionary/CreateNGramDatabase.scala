package org.allenai.dictionary

import scala.io.Source

object CreateNGramDatabase extends App {
  
  val inputPath = args(0)
  val clusterPath = args(1)
  val databasePath = args(2)
  val n = args(3).toInt
  
  val clusters = Annotations.readClusters(clusterPath)
  val content = Source.fromFile(inputPath).getLines.map(_.toLowerCase)
  val annotated = content.map(Annotations.annotate(clusters, _))
  val countedNgrams = NGrams.count(annotated, n)
  
  val db = SqlDatabase(databasePath, n)
  db.create
  db.insert(countedNgrams)
  db.createIndexes
  

}