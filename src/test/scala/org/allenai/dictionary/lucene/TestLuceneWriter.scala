package org.allenai.dictionary.lucene

import org.allenai.common.testkit.ScratchDirectory
import org.allenai.common.testkit.UnitSpec

class TestIndexWriter extends UnitSpec with ScratchDirectory {
  LuceneTestData.writeTestIndex(scratchDir)
  "IndexWriter" should "write to dir" in {
    assert(scratchDir.exists, "scratch dir does not exist")
    assert(!scratchDir.listFiles.isEmpty, "scratchDir is empty")
  }
}
