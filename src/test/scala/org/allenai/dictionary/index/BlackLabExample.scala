package org.allenai.dictionary.index

import nl.inl.blacklab.index.Indexer
import java.io.File
import nl.inl.blacklab.search.Searcher
import nl.inl.blacklab.search.TextPatternProperty
import nl.inl.blacklab.search.TextPatternTerm
import nl.inl.blacklab.search.TextPatternOr
import nl.inl.blacklab.search.TextPatternPrefix
import nl.inl.blacklab.search.sequences.TextPatternRepetition
import nl.inl.blacklab.search.sequences.TextPatternSequence
import nl.inl.blacklab.search.TextPatternCaptureGroup
import org.allenai.dictionary.BlackLabResult
import org.allenai.common.testkit.UnitSpec
import org.allenai.common.testkit.ScratchDirectory
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser

class BlackLabExample extends UnitSpec with ScratchDirectory {

  val clusters = Map.empty[String, String]
  val text = "A teacher is a person who teaches students ."
  val annotated = NlpAnnotate.annotate(text)
  val tokenSentences = for {
    sentence <- annotated
    indexableTokens = sentence.map { t =>
      IndexableToken(t.token.string, t.token.postag, t.lemma, "")
    }
  } yield indexableTokens
  val doc = IndexableText(IdText("doc1", text), tokenSentences)

  println("Here is the document:")
  println(doc.idText.id)
  println(doc.idText.text)
  doc.sentences foreach println
  println

  val indexLocation = scratchDir
  val indexer = new Indexer(indexLocation, true, classOf[AnnotationIndexer])
  val addToMyIndex = CreateIndex.addTo(indexer) _
  addToMyIndex(doc)
  indexer.close

  val searcher = Searcher.open(indexLocation)

  val singularNoun = new TextPatternProperty("pos", new TextPatternTerm("NN"))
  val pluralNoun = new TextPatternProperty("pos", new TextPatternTerm("NNS"))
  val noun = new TextPatternOr(singularNoun, pluralNoun)
  val determiner = new TextPatternProperty("pos", new TextPatternTerm("DT"))
  val adjective = new TextPatternProperty("pos", new TextPatternTerm("JJ"))
  val who = new TextPatternTerm("who")
  val that = new TextPatternTerm("that")
  val which = new TextPatternTerm("which")
  val whWord = new TextPatternOr(who, that, which)
  val beWord = new TextPatternProperty("lemma", new TextPatternTerm("be"))
  val verb = new TextPatternProperty("pos", new TextPatternPrefix("V"))
  val optionalDeterminer = new TextPatternRepetition(determiner, 0, 1)
  val someAdjectives = new TextPatternRepetition(adjective, 0, -1)
  val atLeastOneNoun = new TextPatternRepetition(noun, 1, -1)
  val nounPhrase = new TextPatternSequence(optionalDeterminer, someAdjectives, atLeastOneNoun)

  val isaSeq = new TextPatternSequence(nounPhrase, beWord, nounPhrase)
  val defnSeq = new TextPatternSequence(verb, nounPhrase)

  val textPattern = new TextPatternSequence(
    new TextPatternCaptureGroup(isaSeq, "isa-part"),
    whWord,
    new TextPatternCaptureGroup(defnSeq, "defn-part")
  )

  val limit = 1000
  val hits = searcher.find(textPattern).window(0, limit)
  val transformedHits = BlackLabResult.fromHits(hits).toSeq

  for (hit <- transformedHits) {
    println("Here is the word data:")
    hit.wordData foreach println
    println
    println("It matched this subset of the word data:")
    hit.wordData.slice(hit.matchOffset.start, hit.matchOffset.end) foreach println
    println
    println("Here are the matching named capture groups:")
    for ((groupName, groupOffset) <- hit.captureGroups) {
      println(s"Inside capture group '${groupName}'")
      hit.wordData.slice(groupOffset.start, groupOffset.end) foreach println
      println
    }
  }

  val parsed = CorpusQueryLanguageParser.parse(""" [pos="NN"] """)

}
