import sbt._
import Keys._
import Versions._

object Dependencies {
  val kafka                 =      "org.apache.kafka"              %   "kafka-streams"                  % kafkaVersion
  val jodaTime              =      "joda-time"                     %   "joda-time"                      % jodaTimeVersion
  val jodaConvert           =      "org.joda"                      %   "joda-convert"                   % jodaConvertVersion
  val scalaLogging          =      "com.typesafe.scala-logging"   %%   "scala-logging"                  % scalaLoggingVersion
  val logback               =      "ch.qos.logback"                %   "logback-classic"                % logbackVersion
  val config                =      "com.typesafe"                  %   "config"                         % configVersion
  val cats                  =      "org.typelevel"                %%   "cats"                           % catsVersion
  val alpakka               =      "com.lightbend.akka"           %%   "akka-stream-alpakka-file"       % alpakkaFileVersion
  val reactiveKafka         =      "com.typesafe.akka"            %%   "akka-stream-kafka"              % reactiveKafkaVersion
  val flinkScala            =      "org.apache.flink"             %%   "flink-scala"                    % flinkVersion  % "provided"
  val flinkStreamingScala   =      "org.apache.flink"             %%   "flink-streaming-scala"          % flinkVersion  % "provided"
  val flinkKafka            =      "org.apache.flink"             %%   "flink-connector-kafka-0.11"     % flinkVersion exclude("org.slf4j", "slf4j-log4j12") 
  val flinkKafkaBase        =      "org.apache.flink"             %%   "flink-connector-kafka-base"     % flinkVersion exclude("org.slf4j", "slf4j-log4j12") 
 
  val common = Seq(kafka, jodaTime, jodaConvert, scalaLogging, logback)
  val ingestion = common ++ Seq(alpakka, reactiveKafka, config, cats)
  val app = common ++ Seq(flinkScala, flinkStreamingScala, flinkKafka)
}
