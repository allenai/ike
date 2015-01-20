package org.allenai.dictionary

import org.allenai.scholar.text.Token
import org.allenai.scholar.text.AnnotatedText
import org.allenai.scholar.mentions.{BrownClusterAnnotation => TokenCluster}
import org.allenai.scholar.text.{PosTag => TokenPos}

case class WordData(word: String, attributes: Map[String, String])