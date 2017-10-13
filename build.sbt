name := "ku"

version := "0.1"

scalaVersion := "2.12.3"

scalacOptions ++= Seq("-feature", "-deprecation")

libraryDependencies ++= Seq(
  "io.monix" %% "monix" % "2.3.0",
  "org.slf4j" % "slf4j-log4j12" % "1.8.0-alpha2",
  "com.lihaoyi" %% "scalatags" % "0.6.7",
  "org.scalafx" %% "scalafx" % "8.0.102-R11",
  "com.rometools" % "rome" % "1.8.0",
  "org.jsoup" % "jsoup" % "1.10.3",
  "joda-time" % "joda-time" % "2.9.9"
)
