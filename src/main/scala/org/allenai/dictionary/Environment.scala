package org.allenai.dictionary

import org.allenai.common.immutable.Interval

case class ClusterReplacement(offset: Interval, clusterPrefix: String)

case class EnvironmentState(query: String, replacements: Seq[ClusterReplacement], dictionaries: Map[String, Seq[String]])

case object Environment {
  def replaceClusters(s: String, repls: Seq[ClusterReplacement]): String = 
    replaceClusters(s, repls, 0)
  def replaceClusters(s: String, repls: Seq[ClusterReplacement], start: Int): String = repls match {
    case Nil => s.slice(start, s.size)
    case head :: rest => s.slice(start, head.offset.start) + s"^${head.clusterPrefix}" + replaceClusters(s, rest, head.offset.end) 
  }
  def interpretDictValue(s: String): QueryExpr = Concat(s.split(" ").map(WordToken):_*)
  def parseDict(input: Map[String, Seq[String]]): Map[String, Seq[QueryExpr]] =
    input.mapValues(_.map(interpretDictValue))
  def interpret(env: EnvironmentState, parser: String => QueryExpr): Seq[QueryExpr] = {
    val orignalQuery = env.query
    val replaced = replaceClusters(orignalQuery, env.replacements.sortBy(_.offset))
    val parsed = parser(replaced)
    val parsedDict = parseDict(env.dictionaries) 
    QueryExpr.evalDicts(parsed, parsedDict)
  }

}