import Dependencies._

scalaVersion := "2.11.5"

val dictionaryBuilder = project.in(file(".")).enablePlugins(WebappPlugin)

name := "dictionary-builder"

description := "buildin' them electric dictionaries"

libraryDependencies ++= Seq(
    allenAiCommon,
    allenAiTestkit,
    allenAiDatastore,
    allenAiPipeline,
    nlpstackModule("tokenize"),
    nlpstackModule("postag"),
    nlpstackModule("lemmatize"),
    nlpstackModule("segment"),
    lucene("core"),
    lucene("analyzers-common"),
    lucene("highlighter"),
    lucene("queries"),
    lucene("queryparser")
)

fork in run := true

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

conflictManager := ConflictManager.default

dependencyOverrides ++= Set(allenAiCommon)

dependencyOverrides ++= Set(
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.3",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.2",
  "commons-codec" % "commons-codec" % "1.6",
  "org.apache.commons" % "commons-compress" % "1.8",
  "org.scala-lang" % "scala-reflect" % "2.11.5"
)
