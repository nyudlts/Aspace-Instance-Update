name := "Aspace-Instance-Update"

version := "0.4b"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "org.apache.httpcomponents" % "httpclient" % "4.5.2",
  "org.json4s" %% "json4s-native" % "3.6.0",
  "com.typesafe" % "config" % "1.3.2",
  "org.rogach" %% "scallop" % "3.1.3",
  "org.scalatest" % "scalatest_2.12" % "3.0.5" % "test"
)

assemblyJarName in assembly := "ASInstanceUpdate.jar"

mainClass in assembly := Some("edu.nyu.dlts.aspace.Main")