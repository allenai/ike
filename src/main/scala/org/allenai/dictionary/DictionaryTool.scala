package org.allenai.dictionary

import java.io.File
import org.allenai.common.immutable.Interval
import spray.json._
import DefaultJsonProtocol._
import com.typesafe.config.ConfigFactory

case class WordTokenInfo(word: String, cluster: String, position: TokenPosition)
case object WordTokenInfo {
  implicit val format = jsonFormat3(WordTokenInfo.apply)
}

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
    val wrappedEnv = QueryExpr.captures(parser(env.query)).size match {
      case 0 => env.copy(query = s"(${env.query})")
      case _ => env
    }
    val exprs = Environment.interpret(wrappedEnv, parser)
    reader.execute(exprs).sortBy(hit => -hit.count)
  }

}

case object DictionaryTool {
  def fromConfig: DictionaryTool = {
    val config = ConfigFactory.load
    val indexPath = config.getString("indexPath")
    val clusterPath = config.getString("clusterPath")
    DictionaryTool(indexPath, clusterPath)
  }
}