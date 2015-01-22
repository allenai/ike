package org.allenai.scholar.text

import org.allenai.common.immutable.Interval

trait Annotation {
  /** @return the character range of this annotation
    */
  def interval: Interval
  /** @return the index of the first character in this annotation
    */
  def start: Int = interval.start
  /** @return one plus the index of the last character in this annotation
    */
  def end: Int = interval.end
}

object Annotation {
  // A default implementation
  private case class AnnotationImpl(override val interval: Interval) extends Annotation
  /** @param interval character interval of this annotation in the containing text
    * @return the default implementation of `Annotation` with the given interval
    */
  def apply(interval: Interval): Annotation = AnnotationImpl(interval)
  /** @param start
    * @param end
    * @return an annotation at `Interval.open(start, end)`
    */
  def apply(start: Int, end: Int): Annotation = AnnotationImpl(Interval.open(start, end))
}

/** An annotation for the title of a paper. */
case class Title(interval: Interval) extends Annotation

/** An annotation for the header/title of a section. For example "Related Work" */
case class SectionHeader(interval: Interval, index: Int) extends Annotation

/** An annotation for the body of a paper section. Includes a field for the section number.
  * @param sectionNumber the 1-indexed section number
  */
case class SectionBody(interval: Interval, sectionNumber: Int) extends Annotation

/** An annotation for a paper's abstract section. */
case class Abstract(interval: Interval) extends Annotation

/** An annotation for a sentence. */
case class Sentence(interval: Interval) extends Annotation

/** An annotation for a token. */
case class Token(interval: Interval) extends Annotation

/** An annotation for a part-of-speech tag.
  * @param string the string value of the POS tag
  */
case class PosTag(interval: Interval, string: String) extends Annotation

/** An annotation for a lemma.
  * @param string the string value of the lemma
  */
case class Lemma(interval: Interval, string: String) extends Annotation

/** An annotation for a word cluster.
  * @param string the string value of the cluster id
  */
case class WordCluster(interval: Interval, clusterId: String) extends Annotation
