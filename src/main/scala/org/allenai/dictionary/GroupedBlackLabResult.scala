package org.allenai.dictionary

import org.allenai.common.immutable.Interval

case class KeyedBlackLabResult(key: Interval, result: BlackLabResult)

case class GroupedBlackLabResult(key: String, group: Seq[KeyedBlackLabResult])
