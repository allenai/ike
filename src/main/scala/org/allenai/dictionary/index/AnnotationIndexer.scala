package org.allenai.dictionary.index

import java.io.Reader

import nl.inl.blacklab.index.{ DocIndexerXmlHandlers, Indexer }
import nl.inl.blacklab.index.complex.ComplexFieldProperty
import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting
import org.xml.sax.Attributes

class AnnotationIndexer(indexer: Indexer, fileName: String, reader: Reader)
    extends DocIndexerXmlHandlers(indexer, fileName, reader) {
  val mainProp = getMainProperty
  val punctProp = getPropPunct
  val posProp = addProperty("pos", SensitivitySetting.SENSITIVE_AND_INSENSITIVE)
  val chunkProp = addProperty("chunk", SensitivitySetting.SENSITIVE_AND_INSENSITIVE)
  val lemmaProp = addProperty("lemma", SensitivitySetting.ONLY_SENSITIVE)
  addHandler("/document", new DocumentElementHandler())
  addHandler("word", new WordHandlerBase() {
    def addAttribute(name: String, attrs: Attributes, prop: ComplexFieldProperty): Unit = {
      if (attrs.getValue(name) != null) prop.addValue(attrs.getValue(name))
    }
    def addPos(attrs: Attributes): Unit = addAttribute("pos", attrs, posProp)
    def addChunk(attrs: Attributes): Unit = addAttribute("chunk", attrs, chunkProp)
    def addLemma(attrs: Attributes): Unit = addAttribute("lemma", attrs, lemmaProp)
    def addAttrs(attrs: Attributes): Unit = {
      addPos(attrs)
      addChunk(attrs)
      addLemma(attrs)
    }
    override def startElement(uri: String, ln: String, qName: String, attrs: Attributes): Unit = {
      super.startElement(uri, ln, qName, attrs)
      addAttrs(attrs)
      punctProp.addValue(consumeCharacterContent)
    }
    override def endElement(uri: String, localName: String, qName: String): Unit = {
      super.endElement(uri, localName, qName)
      mainProp.addValue(consumeCharacterContent)
    }
  })
  addHandler("sentence", new InlineTagHandler)
}
