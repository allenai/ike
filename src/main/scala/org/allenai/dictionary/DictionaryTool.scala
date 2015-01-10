package org.allenai.dictionary

import java.io.File
import org.allenai.common.immutable.Interval
import spray.json._
import DefaultJsonProtocol._
import com.typesafe.config.ConfigFactory
import org.allenai.dictionary.lucene.LuceneReader

case class WordTokenInfo(word: String, cluster: String, position: TokenPosition)
case object WordTokenInfo {
  implicit val format = jsonFormat3(WordTokenInfo.apply)
}

case class DictionaryTool(indexPath: String, clusterPath: String) {
  
  val reader = LuceneReader(new File(indexPath))
  val parser = (s: String) => QueryExprParser.parse(s).get
  
  def wordTokenInfo(q: String): Seq[WordTokenInfo] = {
    ???
  }
  
  def execute(env: EnvironmentState): Seq[_] = ???

}
