package org.allenai.scholar.text

import org.allenai.common.immutable.Interval
import scala.collection.immutable.SortedMap

/** Represents some string content with some annotations. An annotation is a reference to a
  * character interval coupled with some data.
  * @param content the string content
  * @param anns a collection of annotations
  */
class AnnotatedText(val content: String, anns: Traversable[Annotation]) {

  /** @return all annotations, sorted by their start index.
    */
  val annotations = anns.toSeq.sortBy(_.start)

  // Index the annotations by their start points.
  private val startPoints = SortedMap(annotations.groupBy(_.start).toSeq: _*)

  /** @return the interval spanning `0` until `content.size`.
    */
  val contentInterval: Interval = Interval.open(0, content.size)

  /** Returns all annotations `a` such that `i.start <= a.start < a.end <= i.end`. The returned
    * annotations are sorted by `a.start`.
    * @param i an Interval
    * @return annotations under `i`
    */
  def annotationsUnder(i: Interval): Seq[Annotation] = {
    // Find all annotations whose start points are in i. Need i.end + 1 because intervals are
    // half-open. range is an O(log n) query against SortedMap
    val startInInterval = startPoints.range(i.start, i.end + 1).values.flatten
    startInInterval.filter(_.end <= i.end).toSeq
  }

  /** @return the annotations `a` that have type `A`
    */
  def typedAnnotations[A <: Annotation: Manifest]: Seq[A] = annotations collect { case a: A => a }

  /** Returns all annotations under `a.interval` that are not equal to `a`.
    * @param a an Annotation
    * @return annotations `b` such that `a.start <= b.start < b.end <= a.end` and `a != b`.
    */
  def annotationsUnder(a: Annotation): Seq[Annotation] =
    annotationsUnder(a.interval) filterNot (_ == a)

  /** Returns all annotations under `i` that have type `A`
    * @param i an Interval
    * @return annotations `a` such that `i.start <= a.start < a.end <= i.end` and `a` has type `A`
    */
  def typedAnnotationsUnder[A <: Annotation: Manifest](i: Interval): Seq[A] =
    annotationsUnder(i) collect { case a: A => a }

  /** Returns all annotations under `a` that are not equal to `a` and have type `A`
    * @param a an Annotation
    * @return annotations `b` such that `a.start <= b.start < b.end <= a.end`, `a != b`, and `a`
    * has type `A`
    */
  def typedAnnotationsUnder[A <: Annotation: Manifest](a: Annotation): Seq[A] =
    annotationsUnder(a) collect { case b: A => b }

  /** @param a
    * @return the content of `a`
    */
  def content(a: Annotation): String = {
    content(a.interval)
  }

  /** @param i
    * @return the content under `i`
    */
  def content(i: Interval): String = {
    require(i subset contentInterval, s"Interval out of bounds: $i not subset of $contentInterval")
    content.slice(i.start, i.end)
  }

  /** @param newAnnotations
    * @return an updated `AnnotatedText` object with the current annotations and `newAnnotations`
    */
  def update(newAnnotations: Seq[Annotation]): AnnotatedText =
    new AnnotatedText(content, annotations ++ newAnnotations)

}
