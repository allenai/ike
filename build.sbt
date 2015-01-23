import Dependencies._

val dictionaryBuilder = project.in(file(".")).enablePlugins(WebappPlugin)

name := "dictionary-builder"

description := "buildin' them electric dictionaries"

libraryDependencies ++= Seq(
    allenAiCommon exclude("com.typesafe", "config"),
    allenAiTestkit,
    akkaActor,
    sprayCan,
    sprayRouting,
    lucene("core"),
    lucene("analyzers-common"),
    lucene("highlighter"),
    lucene("queries"),
    lucene("queryparser")
)

fork in run := true

javaOptions in run ++= Seq("-Xms2G", "-Xmx8G") 

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
