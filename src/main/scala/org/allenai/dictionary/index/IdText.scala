package org.allenai.dictionary.index

import java.io.{ FileInputStream, InputStream, InputStreamReader, File }
import java.net.URL
import java.nio.charset.MalformedInputException
import java.util.zip.GZIPInputStream
import org.allenai.common.{ StreamClosingIterator, Resource, Logging }
import org.allenai.common.ParIterator._
import org.allenai.dictionary.index.WikipediaCorpus.DocumentIterator
import org.apache.commons.io.LineIterator
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
      case "wikipedia" => fromWikipedia(file)
      case _ => throw new IllegalArgumentException(s"format must be flat, directory, or wikipedia")
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

  def fromWikipedia(file: File): Iterator[IdText] =
    StreamClosingIterator(new FileInputStream(file)) { input =>
      val documents = new DocumentIterator(new GZIPInputStream(input))
      documents.map { document =>
        IdText(document.id.toString, document.body)
      }
    }

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

object WikipediaCorpus {
  case class Document(id: Int, url: URL, title: String, body: String)

  class DocumentIterator(inputStream: InputStream) extends Iterator[Document] {
    private val lines = new LineIterator(new InputStreamReader(inputStream, "UTF-8"))

    private var nextDocument: Option[Document] = None
    private def advanceToNextDoc(): Unit = {
      nextDocument = None

      if (lines.hasNext) {
        val docLine = lines.next().trim

        val DocLinePattern = """<doc id="(\d+)" url="([^"]*)" title="([^"]*)">""".r

        // pattern matching on Int
        object Int {
          def unapply(s: String): Option[Int] = try {
            Some(s.toInt)
          } catch {
            case _: java.lang.NumberFormatException => None
          }
        }

        // pattern matching on Url
        object URL {
          def unapply(s: String): Option[URL] = try {
            Some(new URL(s))
          } catch {
            case _: java.net.MalformedURLException => None
          }
        }

        docLine match {
          case DocLinePattern(Int(id), URL(url), title) =>
            // read in the body of the document
            val body = StringBuilder.newBuilder
            while (nextDocument.isEmpty && lines.hasNext) {
              val line = lines.next().trim
              if (line == "</doc>") {
                nextDocument = Some(Document(id, url, title, body.mkString.trim))
              } else {
                body.append(line)
                body.append('\n')
              }
            }
        }
      }

      if (!lines.hasNext)
        lines.close()
    }
    advanceToNextDoc()

    override def hasNext: Boolean = nextDocument.isDefined
    override def next(): Document = nextDocument match {
      case None => throw new NoSuchElementException()
      case Some(doc) => advanceToNextDoc(); doc
    }
  }
}
