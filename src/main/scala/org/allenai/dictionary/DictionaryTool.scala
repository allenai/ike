package org.allenai.dictionary

import java.io.File
import org.allenai.common.immutable.Interval

case class WordTokenInfo(word: String, cluster: String, position: TokenPosition)

case class DictionaryTool(indexPath: String, clusterPath: String) {
  
  val clusters = WordClusters.fromFile(clusterPath)
  val reader = LuceneReader(new File(indexPath))
  val parser = (s: String) => QueryExprParser.parse(s).get
  
  def wordTokenInfo(q: String): Seq[WordTokenInfo] = {
    val expr = parser(q)
    val tokens = QueryExpr.tokens(expr)
    val positions = QueryExpr.tokenPositions(q, expr)
    tokens.zipWithIndex collect {
      case (w: WordToken, i) =>
        val word = w.value
        val cluster = clusters.getOrElse(word.toLowerCase, "")
        val position = positions(i)
        WordTokenInfo(word, cluster, position)
    }
  }
  
  def execute(env: EnvironmentState): Seq[LuceneHit] = {
    val exprs = Environment.interpret(env, parser)
    reader.execute(exprs)
  }

}