name := "dictionary-builder"

description := "buildin' them electric dictionaries"

libraryDependencies += "org.scalatest" % "scalatest_2.10" % "2.2.1" % "test"

libraryDependencies += "com.github.nikita-volkov" % "sext" % "0.2.3"

libraryDependencies += "commons-lang" % "commons-lang" % "2.6"

libraryDependencies ++= Seq(
  "org.scalikejdbc" %% "scalikejdbc"       % "2.2.0",
  "ch.qos.logback"  %  "logback-classic"   % "1.1.2",
  "org.xerial"      % "sqlite-jdbc"        % "3.7.2",
  "org.scalikejdbc" %% "scalikejdbc-config"  % "2.2.0"
)

libraryDependencies += "com.google.code.externalsortinginjava" % "externalsortinginjava" % "0.1.9"

libraryDependencies += "org.allenai.scholar" %% "scholar-ner" % "2014.09.02-5-SNAPSHOT"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

