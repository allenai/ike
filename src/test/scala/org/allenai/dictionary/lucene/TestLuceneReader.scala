package org.allenai.dictionary.lucene

import org.allenai.common.testkit.UnitSpec
import org.allenai.common.testkit.ScratchDirectory
import org.allenai.dictionary.QueryExprParser
import org.allenai.dictionary.QueryExpr

class TestLuceneReader extends UnitSpec with ScratchDirectory {
  LuceneTestData.writeTestIndex(scratchDir)
  val reader = LuceneReader(scratchDir)
  
  def parse(s: String) = QueryExprParser.parse(s).get
  def interpret(q: QueryExpr) = reader.querySemantics(q)
  def execute(s: String) = reader.execute(interpret(parse(s)))
  def getWords(data: Seq[TokenData]) = for {
    token <- data
    attr <- token.attributes.filter(_.key == IndexableSentence.contentAttr).headOption
    value = attr.value
  } yield value
  def matchWords(result: LuceneResult) = {
    val words = getWords(result.sentence.data)
    words.slice(result.matchOffset.start, result.matchOffset.end).mkString(" ")
  }
  def captureWords(result: LuceneResult) = {
    val sentWords = getWords(result.sentence.data)
    val words = result.captureGroups.mapValues(i => sentWords.slice(i.start, i.end).mkString(" "))
    val keys = words.keys.toList.sortBy(result.captureGroups)
    keys map words
  }
  
  "LuceneReader" should "handle basic queries" in {
    val results = execute("I VBP JJ").toSeq
    val words = results.map(matchWords)
    val expected = Set("I eat frozen", "I prefer coconut")
    assert(expected == words.toSet)
  }
  
  it should "handle capture groups" in {
    val results = execute("I (VBP) (JJ)").toSeq
    val words = results.map(captureWords)
    val expected = Set(List("eat", "frozen"), List("prefer", "coconut"))
    assert(expected == words.toSet)
  }
  
  it should "handle plus" in {
    val results = execute("(JJ+) (NN|NNS)")
    val words = results.map(captureWords)
    val expected = Set(List("frozen", "fruit"), List("frozen mango", "chunks"), List("coconut", "oil"))
    assert(expected == words.toSet)
  }
  
}