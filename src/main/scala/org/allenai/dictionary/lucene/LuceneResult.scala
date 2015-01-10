package org.allenai.dictionary.lucene

import org.allenai.common.immutable.Interval

case class LuceneResult(sentence: IndexableSentence, matchOffset: Interval,
    captureGroups: Map[String, Interval])
