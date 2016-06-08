package org.allenai.ike.ml.subsample

import org.allenai.blacklab.search.lucene.DocFieldLengthGetter

/** Stub for DocFieldLengthGetter for testing purposes
  */
class DocFieldLengthGetterStub(docLength: IndexedSeq[Int])
    extends DocFieldLengthGetter(null, "test") {

  def this(docLength: Seq[Int]) = {
    this(docLength.toIndexedSeq)
  }

  override def getFieldLength(doc: Int): Int = {
    docLength(doc)
  }
}
