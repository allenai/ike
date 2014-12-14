package org.allenai.dictionary

import org.scalatest.FlatSpec

class TestNGrams extends FlatSpec {
  val dogs = WordWithCluster("dogs", "0")
  val cats = WordWithCluster("cats", "0")
  val like = WordWithCluster("like", "1")
  val purr = WordWithCluster("purr", "1")
  val period = WordWithCluster(".", "01")
  val clusters = Seq(dogs, cats, like, purr, period).map(x => (x.word, x.cluster)).toMap
  val doc1 = "dogs like cats."
  val doc2 = "cats purr."
  val doc3 = "cats like cats."
  val docs = Seq(doc1, doc2, doc3)
  val annotated = docs.map(Annotations.annotate(clusters, _)).toIterator
  val n = 3

  "NGrams" should "correctly count" in {
    def cng(i: Int, grams: WordWithCluster*): Counted[NGram] = Counted(NGram(grams.toStream), i)
    val grams1 = Set(cng(1, dogs), cng(2, like), cng(4, cats), cng(3, period), cng(1, purr))
    val grams2 = Set(cng(1, dogs, like), cng(2, like, cats), cng(2, cats, period),
        cng(1, cats, purr), cng(1, purr, period), cng(1, cats, like))
    val grams3 = Set(cng(1, dogs, like, cats), cng(2, like, cats, period),
        cng(1, cats, purr, period), cng(1, cats, like, cats))
    val expected = grams1 ++ grams2 ++ grams3
    val returned = NGrams.count(annotated, n)
    val x = returned.toSeq.sortBy(_.value.grams.mkString(" "))
    val y = expected.toSeq.sortBy(_.value.grams.mkString(" "))
    assert(x == y)
  }

}