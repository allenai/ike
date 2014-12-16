name := "dictionary-builder"

description := "buildin' them electric dictionaries"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.2.1" % "test"

libraryDependencies += "com.github.nikita-volkov" % "sext" % "0.2.3"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

libraryDependencies += "org.apache.lucene" % "lucene-core" % "4.8.1"

libraryDependencies += "org.apache.lucene" % "lucene-codecs" % "4.8.1"

libraryDependencies += "org.apache.lucene" % "lucene-analyzers-common" % "4.8.1"

libraryDependencies += "org.apache.lucene" % "lucene-queryparser" % "4.8.1"

