package org.allenai.ike

case class KeyedBlackLabResult(keys: Seq[Interval], result: BlackLabResult)

case class GroupedBlackLabResult(
  keys: Seq[String],
  size: Int,
  relevanceScore: Double,
  results: Iterable[KeyedBlackLabResult]
)
