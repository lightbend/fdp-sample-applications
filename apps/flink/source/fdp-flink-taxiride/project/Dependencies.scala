import Versions._
import sbt._

object Dependencies {
  val jodaTime              =      "joda-time"                     %   "joda-time"                      % jodaTimeVersion
  val jodaConvert           =      "org.joda"                      %   "joda-convert"                   % jodaConvertVersion
  val scalaLogging          =      "com.typesafe.scala-logging"   %%   "scala-logging"                  % scalaLoggingVersion
  val logback               =      "ch.qos.logback"                %   "logback-classic"                % logbackVersion
  val config                =      "com.typesafe"                  %   "config"                         % configVersion
  val cats                  =      "org.typelevel"                %%   "cats"                           % catsVersion
  val alpakka               =      "com.lightbend.akka"           %%   "akka-stream-alpakka-file"       % alpakkaFileVersion
  val reactiveKafka         =      "com.typesafe.akka"            %%   "akka-stream-kafka"              % reactiveKafkaVersion
  val flinkScala            =      "org.apache.flink"             %%   "flink-scala"                    % flinkVersion
  val flinkStreamingScala   =      "org.apache.flink"             %%   "flink-streaming-scala"          % flinkVersion
  val flinkKafka            =      "org.apache.flink"             %%   "flink-connector-kafka"          % flinkVersion exclude("org.slf4j", "slf4j-log4j12") 
 
  val common = Seq(jodaTime, jodaConvert, scalaLogging, logback, config, cats)
  val ingestion = Seq(alpakka, reactiveKafka)
  val app = Seq(flinkScala, flinkStreamingScala, flinkKafka)
}
