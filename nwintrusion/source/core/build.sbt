import sbtassembly.MergeStrategy
import NativePackagerHelper._
import deployssh.DeploySSH._

val scalaLoggingVersion = "3.5.0"
val alpakkaFileVersion = "0.15.1"
val reactiveKafkaVersion = "0.18"
val kafkaVersion = "1.0.0"
val configVersion = "1.3.1"
val catsVersion = "0.9.0"
val spark = "2.2.0"
val logbackVersion = "1.2.3"
val influxDBClientVersion = "2.8"
val kafkaStreamsScalaVersion = "0.1.0"
val scalaHttpVersion = "2.3.0"

allowSnapshot in ThisBuild := true

name := "fdp-nw-intrusion"

organization := "lightbend"

scalaVersion := "2.11.12"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-language:postfixOps",
  "-language:implicitConversions",
  "-language:higherKinds")

mainClass in assembly := Some("com.lightbend.fdp.sample.TransformIntrusionData")

enablePlugins(JavaAppPackaging)
enablePlugins(DeploySSH)

libraryDependencies ++= Seq(
  "org.apache.kafka"              %   "kafka-streams"                  % kafkaVersion,
  "com.typesafe"                  %   "config"                         % configVersion,
  "com.typesafe.scala-logging"   %%   "scala-logging"                  % scalaLoggingVersion,
  "org.typelevel"                %%   "cats"                           % catsVersion,
  "com.lightbend"                %%   "kafka-streams-scala"            % kafkaStreamsScalaVersion,
  "com.lightbend.akka"           %%   "akka-stream-alpakka-file"       % alpakkaFileVersion,
  "com.typesafe.akka"            %%   "akka-stream-kafka"              % reactiveKafkaVersion,
  "ch.qos.logback"                %   "logback-classic"                % logbackVersion,
  "org.apache.spark"             %%   "spark-streaming-kafka-0-10"     % spark,
  "org.apache.spark"             %%   "spark-core"                     % spark % "provided",
  "org.apache.spark"             %%   "spark-streaming"                % spark % "provided",
  "org.apache.spark"             %%   "spark-mllib"                    % spark % "provided",
  "org.apache.spark"             %%   "spark-sql"                      % spark % "provided",
  "org.influxdb"                  %   "influxdb-java"                  % influxDBClientVersion,
  "org.scalaj"                    %   "scalaj-http_2.11"               % scalaHttpVersion


)

//some exclusions and merge strategies for assembly
excludeDependencies ++= Seq(
  "org.spark-project.spark" % "unused"
)

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) => MergeStrategy.last
  case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
  case PathList("org", "slf4j", xs@_*) => MergeStrategy.last
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

resourceDirectory in Compile := (resourceDirectory in Compile).value

mappings in Universal ++= {
  Seq(((resourceDirectory in Compile).value / "application.conf") -> "conf/application.conf") ++
    Seq(((resourceDirectory in Compile).value / "logback.xml") -> "conf/logback.xml")
}

scriptClasspath := Seq("../conf/") ++ scriptClasspath.value

mainClass in Compile := Some("com.lightbend.fdp.sample.TransformIntrusionData")
excludeFilter in Compile := "influx.conf"

deployResourceConfigFiles ++= Seq("deploy.conf")

deployArtifacts ++= Seq(
  ArtifactSSH((packageZipTarball in Universal).value, "/var/www/html/"),
  ArtifactSSH(assembly.value, "/var/www/html/"),
  ArtifactSSH((resourceDirectory in Compile).value / "influx.conf", "/var/www/html") 
)


