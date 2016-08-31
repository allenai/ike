package org.allenai.ike.patterns

import com.typesafe.config.ConfigFactory
import org.allenai.ike.{SearchConfig, SearchRequest}
import scala.collection.JavaConverters._

object PatternUtilities {
  /** Given a Seq of NamedPattern, create a Map where the key is the pattern's name, and the
    * value is a SearchRequest.
    *
    * In this manner you can access a SearchRequest by its name
    *
    * @param namedPatterns A Seq[NamedPattern]
    * @return a Map[String, SearchRequest] where the key is the name from a NamedPattern
    */
  def createSearchers(namedPatterns: Seq[NamedPattern]): Map[String, SearchRequest] = {
    val hugeLimit = Int.MaxValue
    namedPatterns.map{ case namedPattern =>
      (namedPattern.name, SearchRequest(Left(namedPattern.pattern), None, None, SearchConfig(hugeLimit)))
    }.toMap
  }

  /** Takes a config file and creates the NamedPattern extractions patterns
    *
    * @note See the configuration in test/resources for an example of format
    *
    * @param configFile The config file that holds your patterns.
    * @return a Seq[NamedPattern]
    */
  def loadNamedPatterns(configFile: String): Seq[NamedPattern] = {
    val patternConfig = ConfigFactory.load(configFile)
    patternConfig.getConfigList("ExtractionPatterns.patterns").asScala.map { config =>
      NamedPattern(config.getString("name"), config.getString("pattern"))
    }
  }

}
