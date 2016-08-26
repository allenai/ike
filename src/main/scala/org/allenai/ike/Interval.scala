package org.allenai.ike

/** A simple interval class which supports ordering. */
case class Interval(start: Int, end: Int) extends Ordered[Interval] {
  def shift(by: Int): Interval = Interval(this.start + by, this.end + by)

  override def compare(that: Interval): Int =
    if (this.start > that.start) {
      1
    } else if (this.start < that.start) {
      -1
    } else {
      this.length - that.length
    }

  def length: Int = end - start
}
