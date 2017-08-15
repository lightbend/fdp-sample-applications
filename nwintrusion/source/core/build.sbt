import sbtassembly.MergeStrategy

name := "fdp-nw-intrusion"

organization := "lightbend"

version := "0.2"

scalaVersion := "2.11.8"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-language:postfixOps",
  "-language:higherKinds")

mainClass in assembly := Some("com.lightbend.fdp.sample.TransformIntrusionData")

val spark = "2.1.1"

libraryDependencies ++= Seq(
  "org.apache.kafka"      %   "kafka-streams"                  % "0.10.2.1",
  "com.typesafe"          %   "config"                         % "1.3.1",
  "org.typelevel"        %%   "cats"                           % "0.9.0",
  "org.apache.spark"     %%   "spark-streaming-kafka-0-10"     % spark,
  "org.apache.spark"     %%   "spark-core"                     % spark % "provided",
  "org.apache.spark"     %%   "spark-streaming"                % spark % "provided",
  "org.apache.spark"     %%   "spark-mllib"                    % spark % "provided",
  "org.apache.spark"     %%   "spark-sql"                      % spark % "provided"
)

//some exclusions and merge strategies for assembly
excludeDependencies ++= Seq(
  "org.spark-project.spark" % "unused"
)

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) => MergeStrategy.last
  case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
