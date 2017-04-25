lazy val commonSettings = Seq(
  name := "akka-distributed-workers",
  version := "0.1",
  organization := "com.lightbend",
  scalaVersion := "2.11.8",
  test in assembly := {}
)

lazy val app = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    mainClass in assembly := Some("com.lightbend.fdp.sample.Main"),
    assemblyJarName in assembly := s"akka-distributed-workers-${version.value}.jar"
  )


scalaVersion := "2.11.8"
lazy val akkaVersion = "2.5.0"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-language:postfixOps",
  "-language:implicitConversions",
  "-language:higherKinds")

fork in Test := true

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "org.iq80.leveldb" % "leveldb" % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "com.github.scopt" %% "scopt" % "3.5.0",
  "commons-io" % "commons-io" % "2.4" % "test")

assemblyMergeStrategy in assembly := {
  case PathList("application.conf") => MergeStrategy.discard
  case PathList("worker.conf") => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
