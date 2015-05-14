package org.allenai.dictionary.index

import java.io.File
import java.nio.charset.MalformedInputException
import org.allenai.common.{ Resource, Logging }
import org.allenai.common.ParIterator._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.io.Source
import com.typesafe.config.Config
import org.allenai.dictionary.DataFile

case class IdText(id: String, text: String)

case object IdText extends Logging {
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
    (line, i) <- Source.fromFile(file).getLines().zipWithIndex
    id = s"${file.getAbsolutePath}.$i"
    idText = IdText(id, line)
  } yield idText

  def fromDirectory(file: File): Iterator[IdText] = recursiveListFiles(file).parMap({ subFile =>
    if (subFile.isDirectory) {
      None
    } else {
      val id = subFile.getAbsolutePath
      for {
        text <- safeLinesFromFile(subFile)
        idText = IdText(id, text)
      } yield idText
    }
  }, 32).flatten

  def recursiveListFiles(f: File): Iterator[File] = {
    val these = f.listFiles
    these.iterator ++ these.iterator.filter(_.isDirectory).flatMap(recursiveListFiles)
  }

  def safeLinesFromFile(file: File): Option[String] = try {
    Resource.using(Source.fromFile(file)) { source =>
      Some(source.getLines().mkString("\n"))
    }
  } catch {
    case _: MalformedInputException =>
      logger.warn(s"Skipping unreadable file ${file.getName}")
      None
  }
}
