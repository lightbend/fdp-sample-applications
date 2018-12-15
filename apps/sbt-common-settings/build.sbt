// Artifact
organization := "lightbend"

name := "sbt-common-settings"

version := "1.4.0"

sbtPlugin := true


// Sources
scalaSource in Compile := baseDirectory.value / "settings"

libraryDependencies ++= Seq("se.marcuslonnberg" % "sbt-docker" % "1.5.0", "com.typesafe.sbt" % "sbt-native-packager" % "1.3.4")

addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.5.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.4")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.5")
