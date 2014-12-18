package org.allenai.dictionary

import scala.io.Source

object Dictionary {
  
  def fromFile(path: String): Map[String, Seq[String]] = {
    val lines = Source.fromFile(path).getLines
    val entries = for {
      line <- lines
      fields = line.split("\t")
      if fields.size == 2
      name = fields(0)
      label = fields(1)
    } yield (label, name)
    entries.toSeq.groupBy(_._1).mapValues(_.map(_._2).toSeq)
  }

}