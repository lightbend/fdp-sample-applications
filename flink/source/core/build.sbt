import sbtassembly.MergeStrategy, sbtassembly.Assembly
import sbtenforcer.EnforcerPlugin

// NOTE: Versioning of all artifacts is under the control of the `sbt-dynver` plugin and
// enforced by `EnforcerPlugin` found in the `build-plugin` directory.
//
// sbt-dynver: https://github.com/dwijnand/sbt-dynver
//
// The versions emitted follow the following rules:
// |  allowSnapshot  | Case                                                                 | version                        |
// |-----------------| -------------------------------------------------------------------- | ------------------------------ |
// | false (default) | when on tag v1.0.0, w/o local changes                                | 1.0.0                          |
// | true            | when on tag v1.0.0 with local changes                                | 1.0.0+20140707-1030            |
// | true            | when on tag v1.0.0 +3 commits, on commit 1234abcd, w/o local changes | 1.0.0+3-1234abcd               |
// | true            | when on tag v1.0.0 +3 commits, on commit 1234abcd with local changes | 1.0.0+3-1234abcd+20140707-1030 |
// | true            | when there are no tags, on commit 1234abcd, w/o local changes        | 1234abcd                       |
// | true            | when there are no tags, on commit 1234abcd with local changes        | 1234abcd+20140707-1030         |
// | true            | when there are no commits, or the project isn't a git repo           | HEAD+20140707-1030             |
//
// This means DO NOT set or define a `version := ...` setting.
//
// If you have pending changes or a missing tag on the HEAD you will need to set
// `allowSnapshot` to true in order to run `packageBin`.  Otherwise you will get an error
// with the following information:
//   ---------------
// 1. You have uncommmited changes (unclean directory) - Fix: commit your changes and set a tag on HEAD.
// 2. You have a clean directory but no tag on HEAD - Fix: tag the head with a version that satisfies the regex: 'v[0-9][^+]*'
// 3. You have uncommmited changes (a dirty directory) but have not set `allowSnapshot` to `true` - Fix: `set (allowSnapshot in ThisBuild) := true`""".stripMargin)

name := "fdp-flink-taxiride"

organization := "lightbend"

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
