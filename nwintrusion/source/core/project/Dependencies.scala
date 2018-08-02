import sbt._
import Keys._
import Versions._

object Dependencies {

  object Common {
  
    val alpakka           = "com.lightbend.akka"           %%   "akka-stream-alpakka-file"       % alpakkaFileVersion
    val config            = "com.typesafe"                  %   "config"                         % configVersion
    val scalaLogging      = "com.typesafe.scala-logging"   %%   "scala-logging"                  % scalaLoggingVersion

    val cats              = "org.typelevel"                %%   "cats"                           % catsVersion
    val logback           = "ch.qos.logback"                %   "logback-classic"                % logbackVersion

    val influx            = "org.influxdb"                  %   "influxdb-java"                  % influxDBClientVersion
    val scalaj            = "org.scalaj"                    %   "scalaj-http_2.11"               % scalaHttpVersion

    val scopt             = "com.github.scopt"             %%   "scopt"                          % scoptVersion
  }

  object Kafka {

    val ks                = "com.lightbend"                %%   "kafka-streams-scala"            % kafkaStreamsScalaVersion
    val reactiveKafka     = "com.typesafe.akka"            %%   "akka-stream-kafka"              % reactiveKafkaVersion

  }

  object Spark {

    val sparkStreamKafka  = "org.apache.spark"             %%   "spark-streaming-kafka-0-10"     % sparkVersion
    val sparkCore         = "org.apache.spark"             %%   "spark-core"                     % sparkVersion % "provided"
    val sparkStream       = "org.apache.spark"             %%   "spark-streaming"                % sparkVersion % "provided"
    val sparkML           = "org.apache.spark"             %%   "spark-mllib"                    % sparkVersion % "provided"
    val sparkSql          = "org.apache.spark"             %%   "spark-sql"                      % sparkVersion % "provided"

  }

  val commonDependencies: Seq[ModuleID] = Seq(Common.alpakka,
    Common.logback,
    Common.scalaLogging,
    Common.cats,
    Common.scopt
  )

  val visualizationDependencies: Seq[ModuleID] = Seq(Common.influx, Common.scalaj)

  val kafkaDependencies: Seq[ModuleID] = Seq(Kafka.ks, Kafka.reactiveKafka)

  val sparkDependencies: Seq[ModuleID] = Seq(Spark.sparkStreamKafka,
    Spark.sparkCore,
    Spark.sparkStream,
    Spark.sparkML,
    Spark.sparkSql
  )

  val ingestionDependencies: Seq[ModuleID] = commonDependencies ++ kafkaDependencies
  val anomalyDependencies: Seq[ModuleID] = commonDependencies ++ kafkaDependencies ++ visualizationDependencies ++ sparkDependencies
  val batchKMeansDependencies: Seq[ModuleID] = commonDependencies ++ sparkDependencies
}
