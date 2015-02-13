package org.allenai.dictionary.index

import java.io.File
import scala.io.Source
import com.typesafe.config.Config
import org.allenai.dictionary.DataFile

case class IdText(id: String, text: String)

case object IdText {
  def fromConfig(config: Config): Iterator[IdText] = {
    val format = config.getString("format")
    val file = DataFile.fromConfig(config)
    format match {
      case "flat" => fromFlatFile(file)
      case "directory" => fromDirectory(file)
      case _ => throw new IllegalArgumentException(s"format must be flat or directory")
    }
  }
  def fromFlatFile(file: File): Iterator[IdText] = for {
    (line, i) <- Source.fromFile(file).getLines.zipWithIndex
    id = s"${file.getAbsolutePath}.${i}"
    idText = IdText(id, line)
  } yield idText
  def fromDirectory(file: File): Iterator[IdText] = for {
    subFile <- recursiveListFiles(file).toIterator
    if !subFile.isDirectory
    id = subFile.getAbsolutePath
    text = Source.fromFile(subFile).getLines.mkString("\n")
    idText = IdText(id, text)
  } yield idText
  def recursiveListFiles(f: File): Array[File] = {
    val these = f.listFiles
    these ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
  }
}