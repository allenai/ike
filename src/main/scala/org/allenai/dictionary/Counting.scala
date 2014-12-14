package org.allenai.dictionary

import java.io.File
import java.io.PrintWriter
import com.google.code.externalsorting.ExternalSort
import scala.io.Source

object Counting {
  
  /** External sort using filesystem */
  def sort(iter: Iterator[String]): Iterator[String] = {
    val unsortedFile = File.createTempFile("unsorted", ".tsv")
    val unsortedWriter = new PrintWriter(unsortedFile)
    iter foreach unsortedWriter.println
    unsortedWriter.close
    val sortedFile = File.createTempFile("sorted", ".tsv")
    ExternalSort.sort(unsortedFile, sortedFile)
    Source.fromFile(sortedFile).getLines
  }
  
  /** Equivalent to `uniq -c` in unix.
   */
  def uniqc[A](iter: Iterator[A]): Iterator[Counted[A]] = {
    val buffered = iter.buffered
    if (buffered.hasNext) {
      val head = buffered.head
      val zero = Counted(head, 1)
      val tallied = iter.scanLeft(zero)(tally)
      val padded = tallied ++ Iterator(zero)
      max(padded)
    } else {
      Iterator.empty
    }
  }
  def tally[A](counted: Counted[A], b: A): Counted[A] = counted match {
    case Counted(a, i) if a == b => Counted(a, i + 1)
    case _ => Counted(b, 1)
  }
  def max[A](iter: Iterator[Counted[A]]): Iterator[Counted[A]] = for {
    Seq(left, right) <- iter.sliding(2)
    if left.value != right.value || !iter.hasNext // if on the last element, always return
  } yield left
}

case class Counted[A](value: A, count: Int)