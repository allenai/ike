name := "dictionary-builder"

description := "buildin' them electric dictionaries"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.2.1" % "test"

libraryDependencies += "com.github.nikita-volkov" % "sext" % "0.2.3"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

libraryDependencies += "org.apache.lucene" % "lucene-core" % "4.8.1"

libraryDependencies += "org.apache.lucene" % "lucene-codecs" % "4.8.1"

libraryDependencies += "org.apache.lucene" % "lucene-analyzers-common" % "4.8.1"

libraryDependencies += "org.apache.lucene" % "lucene-queryparser" % "4.8.1"

libraryDependencies += "org.apache.lucene" % "lucene-highlighter" % "4.8.1"

libraryDependencies += "org.allenai.common" %% "common-core" % "2014.09.09-0"

libraryDependencies ++= {
  val akkaV = "2.3.6"
  val sprayV = "1.3.1"
  Seq(
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-routing" % sprayV,
    "io.spray"            %%  "spray-testkit" % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
    "org.specs2"          %%  "specs2-core"   % "2.3.7" % "test"
  )
}
