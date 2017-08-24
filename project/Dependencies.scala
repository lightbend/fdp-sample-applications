import sbt._
import Versions._

object Dependencies {

  implicit class Exclude(module: ModuleID) {
    def log4jExclude: ModuleID =
      module excludeAll(ExclusionRule("log4j"))

    def driverExclusions: ModuleID =
      module.log4jExclude.exclude("com.google.guava", "guava")
        .excludeAll(ExclusionRule("org.slf4j"))
  }

  val akkaStream        = "com.typesafe.akka"       % "akka-stream_2.11"                % AkkaStreams
  val akkaStreamKafka   = "com.typesafe.akka"       % "akka-stream-kafka_2.11"          % AkkaStreamsKafka
  val akkaHttpCore      = "com.typesafe.akka"       % "akka-http_2.11"                  % AkkaHTTP
  val akkaActor         = "com.typesafe.akka"       % "akka-actor_2.11"                 % Akka
  val akkaSlf4j         = "com.typesafe.akka"       % "akka-slf4j_2.11"                 % Akka
  val akkaCluster       = "com.typesafe.akka"       % "akka-cluster_2.11"               % Akka
  val curator           = "org.apache.curator"      % "curator-test"                    % Curator                           // ApacheV2
  val jodaTime          = "joda-time"               % "joda-time"                       % JodaTime                          // ApacheV2
  val jodaConvert       = "org.joda"                % "joda-convert"                    % JodaConvert                       // ApacheV2
  val json4sCore        = "org.json4s"              % "json4s-core_2.11"                % Json4s                            // ApacheV2
  val json4sJackson     = "org.json4s"              % "json4s-jackson_2.11"             % Json4s                            // ApacheV2
  val json4sNative      = "org.json4s"              % "json4s-native_2.11"              % Json4s                            // ApacheV2
  val kafka             = "org.apache.kafka"        % "kafka_2.11"                      % Kafka                             // ApacheV2
  val sparkCore         = "org.apache.spark"        % "spark-core_2.11"                 % Spark            % "provided"     // ApacheV2
  val sparkCatalyst     = "org.apache.spark"        % "spark-catalyst_2.11"             % Spark            % "provided"     // ApacheV2
  val sparkKafkaStreaming = "org.apache.spark"      % "spark-streaming-kafka-0-10_2.11" % Spark                             // ApacheV2
  val sparkStreaming    = "org.apache.spark"        % "spark-streaming_2.11"            % Spark            % "provided"     // ApacheV2
  val sparkSQL          = "org.apache.spark"        % "spark-sql_2.11"                  % Spark            % "provided"     // ApacheV2
  val logback           = "ch.qos.logback"          % "logback-classic"                 % Logback                           // LGPL
  val slf4jApi          = "org.slf4j"               % "slf4j-api"                       % Slf4j                             // MIT
  val slf4jLog          = "org.slf4j"               % "slf4j-log4j12"                   % Slf4j                             // MIT
  val sparkCassandra    = "com.datastax.spark"      % "spark-cassandra-connector_2.11"  % SparkCassandra                    // ApacheV2

  val scalaPBRuntime    = "com.trueaccord.scalapb"  %% "scalapb-runtime"                % com.trueaccord.scalapb.compiler.Version.scalapbVersion % "protobuf"
  val scalaPBGRPC       =  "com.trueaccord.scalapb" %% "scalapb-runtime-grpc"           % com.trueaccord.scalapb.compiler.Version.scalapbVersion
  val grpcNetty         = "io.grpc"                 %  "grpc-netty"                     % GRPCNettyVersion
  val scalaPBJSON       = "com.trueaccord.scalapb"  %% "scalapb-json4s"                 % ScalaPBJSONVersion
  val scalaHTTP         = "org.scalaj"              % "scalaj-http_2.11"                % ScalaHTTPVersion

  val influxDBClient    = "org.influxdb"            % "influxdb-java"                   % influxDBClientVersion


  val connector = Seq(
    sparkCassandra
      .exclude("joda-time", "joda-time")
      .exclude("org.joda", "joda-convert")
      .exclude("org.slf4j", "slf4j-api")
      .exclude("io.netty", "netty-all")
      .exclude("org.apache.spark", "spark-core_2.11")
      .exclude("org.apache.spark", "spark-unsafe_2.11")
      .exclude("org.apache.spark", "spark-hive_2.11")
      .exclude("org.apache.spark", "spark-sql_2.11")
      .exclude("org.apache.spark", "spark-catalyst_2.11")
      .exclude("org.apache.spark", "spark-streaming_2.11")
  )
  val logging = Seq(logback.exclude("org.slf4j", "slf4j-api"), slf4jApi)
  val time = Seq(jodaConvert, jodaTime)
  val akka = Seq(
    akkaActor,
    akkaSlf4j.
      exclude("org.slf4j", "slf4j-api").
      exclude("org.slf4j", "slf4j-log4j12"),
    akkaHttpCore, akkaStream, akkaCluster, akkaStreamKafka)
  val json = Seq(json4sCore, json4sJackson, json4sNative)
  val grpc = Seq(scalaPBRuntime, scalaPBGRPC, grpcNetty, scalaPBJSON)
  val spark = Seq(sparkCore, sparkStreaming,
    sparkKafkaStreaming
      .exclude("org.apache.spark", "spark-tags_2.11")
      .exclude("org.apache.spark", "spark-streaming_2.11")
      .exclude("org.apache.kafka", "kafka_2.11")
      .exclude("org.spark-project.spark", "unused")
      .exclude("org.apache.spark", "spark-core_2.11"),
    sparkCatalyst, sparkSQL)

  /** Module deps */
  val client = logging ++ akka ++ json ++ grpc ++ Seq(
    scalaHTTP.exclude("com.fasterxml.jackson.module", "jackson-module-scala_2.11"))
  val core = logging ++ time ++ connector ++ spark ++ Seq(
    curator.
      exclude("io.netty", "netty"),
    kafka.
      exclude("org.slf4j", "slf4j-log4j12").
      exclude("io.netty", "netty"))
  val app = connector  ++ spark ++ Seq(influxDBClient)
}  