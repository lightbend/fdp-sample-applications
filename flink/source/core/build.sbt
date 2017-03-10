import sbtassembly.MergeStrategy

name := "fdp-flink-taxiride"

organization := "lightbend"

version := "0.1"

scalaVersion := "2.11.8"

// Java then Scala for main sources
compileOrder in Compile := CompileOrder.JavaThenScala

// allow circular dependencies for test sources
compileOrder in Test := CompileOrder.Mixed

javacOptions += "-g:none"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-language:postfixOps",
  "-language:higherKinds")

mainClass in assembly := Some("com.lightbend.fdp.sample.flink.TravelTimePrediction")

val flinkVersion = "1.2.0"

libraryDependencies ++= Seq(
  "org.apache.kafka"      %   "kafka-streams"                  % "0.10.1.0",
  "com.typesafe"          %   "config"                         % "1.3.1",
  "joda-time"             %   "joda-time"                      % "2.7",
  "org.joda"              %   "joda-convert"                   % "1.8.1",
  "org.typelevel"        %%   "cats"                           % "0.8.0",
  "org.apache.flink"     %%   "flink-scala"                    % flinkVersion,
  "org.apache.flink"     %%   "flink-streaming-scala"          % flinkVersion, //  % "provided",
  "org.apache.flink"     %%   "flink-connector-kafka-0.10"     % flinkVersion // % "provided"
)

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) => MergeStrategy.last
  case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
