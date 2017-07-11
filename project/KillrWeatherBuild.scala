import sbt._
import sbt.Keys._

object KillrWeatherBuild extends Build {
  import Settings._

  scalaVersion := "2.11.11"


  lazy val root = (project in file(".")).
    aggregate(core, app, clients)

  lazy val core = (project in file("./killrweather-core"))
    .settings(defaultSettings:_*)
    .settings(libraryDependencies ++= Dependencies.core)

  lazy val app = (project in file("./killrweather-app"))
    .settings(defaultSettings:_*)
    .settings(libraryDependencies ++= Dependencies.app)
    .dependsOn(core)

  lazy val clients = (project in file("./killrweather-clients"))
    .settings(defaultSettings:_*)
    .settings(libraryDependencies ++= Dependencies.client)
    .dependsOn(core)
}

/** To use the connector, the only dependency required is:
  * "com.datastax.spark"  %% "spark-cassandra-connector" and possibly slf4j.
  * The others are here for other non-spark core and streaming code.
  */
object Dependencies {
  import Versions._

  implicit class Exclude(module: ModuleID) {
    def log4jExclude: ModuleID =
      module excludeAll(ExclusionRule("log4j"))

    def driverExclusions: ModuleID =
      module.log4jExclude.exclude("com.google.guava", "guava")
      .excludeAll(ExclusionRule("org.slf4j"))
  }

  object Compile {

    val curator           = "org.apache.curator"  % "curator-test"                        % Curator                 // ApacheV2
    val driver            = "com.datastax.cassandra" % "cassandra-driver-core"            % CassandraDriver driverExclusions // ApacheV2
    val jodaTime          = "joda-time"           % "joda-time"                           % JodaTime                // ApacheV2
    val jodaConvert       = "org.joda"            % "joda-convert"                        % JodaConvert             // ApacheV2
    val kafka             = "org.apache.kafka"    % "kafka_2.11"                          % Kafka                   // ApacheV2
    val spark             = "org.apache.spark"    % "spark-core_2.11"                     % Spark                   // ApacheV2
    val sparkCatalyst     = "org.apache.spark"    %  "spark-catalyst_2.11"                % Spark                   // ApacheV2
    val sparkKafkaStreaming = "org.apache.spark"  % "spark-streaming-kafka-0-10_2.11"     % Spark                   // ApacheV2
    val sparkStreaming    = "org.apache.spark"    % "spark-streaming_2.11"                % Spark                   // ApacheV2
    val sparkSQL          = "org.apache.spark"    % "spark-sql_2.11"                      % Spark                   // ApacheV2
    val logback           = "ch.qos.logback"      % "logback-classic"                     % Logback                 // LGPL
    val slf4jApi          = "org.slf4j"           % "slf4j-api"                           % Slf4j                   // MIT
    val slf4jLog          = "org.slf4j"           % "slf4j-log4j12"                       % Slf4j                   // MIT
    val sparkCassandra    = "com.datastax.spark"  %  "spark-cassandra-connector_2.11"     % SparkCassandra          // ApacheV2
  }

  import Compile._

  val connector = Seq(driver, sparkCassandra, sparkCatalyst, sparkSQL)

  val logging = Seq(logback, slf4jApi)

  val time = Seq(jodaConvert, jodaTime)


  /** Module deps */
  val client = logging

  val core = logging ++ time ++ connector ++ Seq(curator,kafka)

  val app = connector  ++ Seq(spark, sparkStreaming, sparkKafkaStreaming)
}

