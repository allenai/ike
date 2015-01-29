package org.allenai.dictionary

case class Dictionary(name: String, positive: Seq[String], negative: Seq[String])

case object Dictionary {
  def wordSeq(s: String): QSeq = {
    val words = s.split(" ")
    val subExprs = words map QWord.apply
    QSeq(subExprs)
  }
  def positiveDisj(d: Dictionary): QDisj = {
    val seqs = d.positive map wordSeq
    QDisj(seqs)
  }
}
