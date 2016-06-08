import Dependencies._

scalaVersion := "2.11.5"

val ike = project.in(file(".")).enablePlugins(WebappPlugin)

organization := "org.allenai"

name := "ike"

homepage := Some(url("https://ike.allenai.org"))

description := "buildin' them electric tables"

scmInfo := Some(ScmInfo(
  url("https://github.com/allenai/ike"),
  "https://github.com/allenai/ike.git"))

pomExtra :=
  <developers>
    <developer>
      <id>allenai-dev-role</id>
      <name>Allen Institute for Artificial Intelligence</name>
      <email>dev-role@allenai.org</email>
    </developer>
  </developers>

libraryDependencies ++= Seq(
    allenAiCommon,
    allenAiTestkit,
    allenAiDatastore,
    nlpstackModule("tokenize") exclude("org.allenai", "datastore_2.11"),
    nlpstackModule("postag") exclude("org.allenai", "datastore_2.11"),
    nlpstackModule("chunk") exclude("org.allenai", "datastore_2.11"),
    nlpstackModule("lemmatize") exclude("org.allenai", "datastore_2.11"),
    nlpstackModule("segment") exclude("org.allenai", "datastore_2.11"),
    lucene("core"),
    lucene("analyzers-common"),
    lucene("highlighter"),
    lucene("queries"),
    lucene("queryparser"),
    "com.typesafe.slick" %% "slick" % "2.1.0",
    "com.github.tminglei" %% "slick-pg" % "0.8.2",
    "com.typesafe.play" %% "play-json" % "2.3.8",
    "org.postgresql" % "postgresql" % "9.4-1201-jdbc41",
    "org.allenai.blacklab" %% "blacklab" % "1.0-ALLENAI-13",
    "org.allenai.word2vec" %% "word2vecjava" % "1.0.1",
    "com.google.guava" % "guava" % "18.0",
    "org.apache.thrift" % "libthrift" % "0.9.1",
    sprayModule("caching"),
    "com.papertrailapp" % "logback-syslog4j" % "1.0.0",
    scopt)

javaOptions in Revolver.reStart += "-Xmx14G"

mainClass in Revolver.reStart := Some("org.allenai.ike.IkeToolWebapp")

fork in run := true

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

conflictManager := ConflictManager.default

dependencyOverrides ++= Set(
  "org.allenai.common" %% "common-core" % "1.0.13",
  sprayJson,
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.3",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.2",
  "commons-codec" % "commons-codec" % "1.6",
  "org.apache.commons" % "commons-compress" % "1.8",
  "org.scala-lang" % "scala-reflect" % "2.11.5"
)
