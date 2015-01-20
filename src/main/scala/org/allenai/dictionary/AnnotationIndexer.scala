package org.allenai.dictionary

import nl.inl.blacklab.index.DocIndexerXmlHandlers
import nl.inl.blacklab.index.Indexer
import java.io.Reader
import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting
import org.xml.sax.Attributes
import nl.inl.blacklab.index.complex.ComplexFieldProperty

class AnnotationIndexer(indexer: Indexer, fileName: String, reader: Reader) extends DocIndexerXmlHandlers(indexer, fileName, reader) {
  val mainProp = getMainProperty
  val punctProp = getPropPunct
  val posProp = addProperty("pos", SensitivitySetting.SENSITIVE_AND_INSENSITIVE)
  val clusterProp = addProperty("cluster", SensitivitySetting.ONLY_SENSITIVE)
  addHandler("/document", new DocumentElementHandler())
  addHandler("token", new WordHandlerBase() {
    def addAttribute(name: String, attrs: Attributes, prop: ComplexFieldProperty): Unit = {
      if (attrs.getValue(name) != null) prop.addValue(attrs.getValue(name))
    }
    def addPos(attrs: Attributes): Unit = addAttribute("pos", attrs, posProp)
    def addCluster(attrs: Attributes): Unit = addAttribute("cluster", attrs, clusterProp)
    def addAttrs(attrs: Attributes): Unit = {
      addPos(attrs)
      addCluster(attrs)
    }
    override def startElement(uri: String, localName: String, qName: String, attrs: Attributes): Unit = {
      super.startElement(uri, localName, qName, attrs)
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
