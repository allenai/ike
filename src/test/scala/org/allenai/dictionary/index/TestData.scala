package org.allenai.dictionary.index

import java.io.File
import nl.inl.blacklab.index.Indexer
import java.io.StringReader
import nl.inl.blacklab.search.Searcher

object TestData {

  val lemmas = Map(
    "I" -> "i",
    "like" -> "like",
    "mango" -> "mango",
    "." -> ".",
    "It" -> "it",
    "tastes" -> "taste",
    "great" -> "great",
    "hate" -> "hate",
    "those" -> "this",
    "bananas" -> "banana",
    "They" -> "they",
    "taste" -> "taste",
    "not" -> "not"
  )

  val posTags = Map(
    "I" -> "PRP",
    "like" -> "VBP",
    "mango" -> "NN",
    "." -> ".",
    "It" -> "PRP",
    "tastes" -> "VBP",
    "great" -> "JJ",
    "hate" -> "VBP",
    "those" -> "DT",
    "bananas" -> "NNS",
    "They" -> "PRP",
    "taste" -> "VBP",
    "not" -> "RB"
  )

  val chunks = Map(
    "doc1" -> "BE-NP BE-VP BE-ADJP O".split(' '),
    "doc2" -> "BE-NP BE-VP BE-ADJP O".split(' '),
    "doc3" -> "BE-NP NE-VP B-NP E-NP O".split(' '),
    "doc4" -> "BE-NP BE-VP O BE-ADJP O".split(' ')
  )

  val idTexts = Seq(
    IdText("doc1", "I like mango ."),
    IdText("doc2", "It tastes great ."),
    IdText("doc3", "I hate those bananas ."),
    IdText("doc4", "They taste not great .")
  )

  val indexableTexts = idTexts map { idText =>
    val words = idText.text.split(" ")
    val ps = words.map(posTags.getOrElse(_, ""))
    val ls = words.map(lemmas.getOrElse(_, ""))
    val ch = chunks(idText.id)
    val tokens = List(words, ps, ls, ch).transpose map {
      case List(w, p, l, c) => IndexableToken(w, p, l, c)
    }
    IndexableText(idText, List(tokens))
  }

  def createTestIndex(path: File): Unit = {
    val indexer = new Indexer(path, true, classOf[AnnotationIndexer])
    indexableTexts foreach CreateIndex.addTo(indexer)
    indexer.close
  }

  def testSearcher(path: File): Searcher = Searcher.open(path)

}
