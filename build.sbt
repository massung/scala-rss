name := "ku"

version := "0.1"

scalaVersion := "2.12.3"

scalacOptions ++= Seq("-feature", "-deprecation")

libraryDependencies ++= Seq(
  "io.monix" %% "monix" % "2.3.0",
  "ch.qos.logback" % "logback-classic" % "1.0.13",
  "org.scalaj" %% "scalaj-http" % "2.3.0",
  "com.lihaoyi" %% "scalatags" % "0.6.7",
  "org.scalafx" %% "scalafx" % "8.0.102-R11",
  "com.rometools" % "rome" % "1.8.0",
  "org.jsoup" % "jsoup" % "1.10.3",
  "joda-time" % "joda-time" % "2.9.9"
)
