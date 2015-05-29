import Dependencies._

scalaVersion := "2.11.5"

val okcorpus = project.in(file(".")).enablePlugins(WebappPlugin)

name := "okcorpus"

description := "buildin' them electric tables"

libraryDependencies ++= Seq(
    allenAiCommon,
    allenAiTestkit,
    allenAiDatastore,
    nlpstackModule("tokenize"),
    nlpstackModule("postag"),
    nlpstackModule("lemmatize"),
    nlpstackModule("segment"),
    lucene("core"),
    lucene("analyzers-common"),
    lucene("highlighter"),
    lucene("queries"),
    lucene("queryparser"),
    "com.typesafe.slick" %% "slick" % "2.1.0",
    "com.github.tminglei" %% "slick-pg" % "0.8.2",
    "com.typesafe.play" %% "play-json" % "2.3.8",
    "org.postgresql" % "postgresql" % "9.4-1201-jdbc41",
    "nl.inl" %% "blacklab" % "1.0-ALLENAI-3",
    //"com.medallia.word2vec" % "Word2VecJava" % "0.9.0",
    "com.google.guava" % "guava" % "18.0",
    "org.apache.thrift" % "libthrift" % "0.9.1",
    scopt)

javaOptions in Revolver.reStart += "-Xmx14G"

mainClass in Revolver.reStart := Some("org.allenai.dictionary.DictionaryToolWebapp")

fork in run := true

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

conflictManager := ConflictManager.default

dependencyOverrides ++= Set(
  allenAiCommon,
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.3",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.2",
  "commons-codec" % "commons-codec" % "1.6",
  "org.apache.commons" % "commons-compress" % "1.8",
  "org.scala-lang" % "scala-reflect" % "2.11.5"
)
