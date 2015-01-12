package org.allenai.dictionary.lucene

import org.allenai.common.testkit.UnitSpec
import org.allenai.common.testkit.ScratchDirectory

class TestLuceneReader extends UnitSpec with ScratchDirectory {
  LuceneTestData.writeTestIndex(scratchDir)
  val reader = LuceneReader(scratchDir)
  val content = LuceneTestData.contentEq _
  val pos = LuceneTestData.posEq _
  val posRegex = (s: String) => LuceneTestData.posRegex(s, reader.reader)
  val contentRegex = (s: String) => LuceneTestData.contentRegex(s, reader.reader)
  
  "LuceneReader" should "fetch expected data" in {
    val query = LSeq(content("This"), content("work"), content("is"), content("a"))
    val results = reader.execute(query).toSeq
    assert(results.size == 1)
    val r = results.head
    val words = r.sentence.attributeSeq(IndexableSentence.contentAttr)
    val matchWords = words.slice(r.matchOffset.start, r.matchOffset.end).mkString(" ")
    assert(matchWords == "This work is a")
  }
  
  it should "handle pos tags" in {
    val query = LSeq(content("This"), pos("NN"), content("is"), pos("DT"))
    val results = reader.execute(query).toSeq
    assert(results.size == 1)
    val r = results.head
    val words = r.sentence.attributeSeq(IndexableSentence.contentAttr)
    val matchWords = words.slice(r.matchOffset.start, r.matchOffset.end).mkString(" ")
    assert(matchWords == "This work is a")
  }
  
  it should "handle token regexes" in {
    val query = LSeq(content("we"), posRegex("V.*"), posRegex("V.*"))
    val results = for {
      row <- reader.execute(query)
      words = row.sentence.attributeSeq(IndexableSentence.contentAttr)
      matchWords = words.slice(row.matchOffset.start, row.matchOffset.end).mkString(" ")
    } yield matchWords
    val expected = Set("we have adopted", "we have described", "we have chosen")
    assert(expected == results.toSet)
  }
  
  it should "handle capture groups" in {
    val name = "myGroup"
    val query = LSeq(content("we"), content("have"), LCapture(posRegex("V.*"), name))
    val results = for {
      row <- reader.execute(query)
      words = row.sentence.attributeSeq(IndexableSentence.contentAttr)
      groupOffset <- row.captureGroups.get(name)
      matchWords = words.slice(groupOffset.start, groupOffset.end).mkString(" ")
    } yield matchWords
    val expected = Set("adopted", "described", "chosen")
    assert(expected == results.toSet)
  }
  
  it should "handle two capture groups" in {
    val name1 = "group1"
    val name2 = "group2"
    val query = LSeq(LCapture(pos("VBG"), name1), content("the"), LCapture(contentRegex("s.*"), name2))
    val results = for {
      row <- reader.execute(query)
      words = row.sentence.attributeSeq(IndexableSentence.contentAttr)
      offset1 = row.captureGroups(name1)
      offset2 = row.captureGroups(name2)
      words1 = words.slice(offset1.start, offset1.end).mkString(" ")
      words2 = words.slice(offset2.start, offset2.end).mkString(" ")
    } yield (words1, words2)
    val expected = Set(("comparing", "student"), ("using", "system"))
    assert(expected == results.toSet)
  }
}