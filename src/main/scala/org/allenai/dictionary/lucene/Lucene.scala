package org.allenai.dictionary.lucene

import org.apache.lucene.util.Version
import org.apache.lucene.document.FieldType

object Lucene {
  val version = Version.LUCENE_48
  val fieldName = "text"
  val fieldType = {
    val f = new FieldType
    f.setIndexed(true)
    f.setStored(true)
    f.setStoreTermVectors(true)
    f.setStoreTermVectorOffsets(true)
    f.freeze
    f
  }
}