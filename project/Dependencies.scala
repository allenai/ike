import org.allenai.plugins.CoreDependencies

import sbt._
import sbt.Keys._

object Dependencies extends CoreDependencies {
  val allenAiDatastore = "org.allenai.datastore" %% "datastore" % "1.0.7" excludeAll (
    // This conflicts with aws-java-sdk 1.7.4 in hadoop.
    ExclusionRule(organization = "com.amazonaws", name = "aws-java-sdk-s3")
  )

  def hadoopModule(id: String) = "org.apache.hadoop" % id % "2.7.2" excludeAll (
    ExclusionRule(organization = "com.google.guava"),
    ExclusionRule(organization = "javax.servlet"),
    ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12")
  )

  val luceneGroup = "org.apache.lucene"
  val luceneVersion = "4.2.1"
  def lucene(part: String) = luceneGroup % s"lucene-${part}" % luceneVersion

  val nlpstackVersion = "1.10"
  def nlpstackModule(id: String) = "org.allenai.nlpstack" %% s"nlpstack-${id}" % nlpstackVersion

  def sparkModule(id: String) = "org.apache.spark" %% s"spark-$id" % "1.6.1" excludeAll (
    ExclusionRule(organization = "com.google.guava"),
    ExclusionRule(organization = "org.apache.commons"),
    ExclusionRule(organization = "org.codehaus.jackson"),
    ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12")
  )
}
