import org.allenai.plugins.CoreDependencies

import sbt._
import sbt.Keys._

object Dependencies extends CoreDependencies {
  val luceneGroup = "org.apache.lucene"
  val luceneVersion = "4.2.1"
  def lucene(part: String) = luceneGroup % s"lucene-${part}" % luceneVersion
}
