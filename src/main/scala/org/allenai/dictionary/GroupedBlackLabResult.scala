package org.allenai.dictionary

import org.allenai.common.immutable.Interval

case class KeyedBlackLabResult(keys: Seq[Interval], result: BlackLabResult)

case class GroupedBlackLabResult(key: String, size: Int, group: Seq[KeyedBlackLabResult])
