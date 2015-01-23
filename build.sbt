import Dependencies._

val dictionaryBuilder = project.in(file(".")).enablePlugins(DeployPlugin)

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
    lucene("queryparser"),
    "org.apache.logging.log4j" % "log4j-core" % "2.1"
)

fork in run := true

javaOptions in run ++= Seq("-Xms2G", "-Xmx8G") 

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
