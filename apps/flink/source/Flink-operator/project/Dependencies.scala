/**
  * Created by boris on 7/14/17.
  */
import sbt._

object Dependencies {
  val reactiveKafka         = "com.typesafe.akka"       % "akka-stream-kafka_2.11"        % reactiveKafkaVersion

  val akkaStreamTyped       = "com.typesafe.akka"       %% "akka-stream-typed"            % akkaVersion
  val akkaHttp              = "com.typesafe.akka"       % "akka-http_2.11"                % akkaHttpVersion
  val akkaHttpJsonJackson   = "de.heikoseeberger"       % "akka-http-jackson_2.11"        % akkaHttpJsonVersion
  val akkatyped             = "com.typesafe.akka"       %% "akka-actor-typed"             % akkaVersion


  val kafka                 = "org.apache.kafka"        %% "kafka"                        % kafkaVersion
  val curator               = "org.apache.curator"      % "curator-test"                  % Curator
  
  val typesafeConfig        = "com.typesafe"            %  "config"                       % TypesafeConfigVersion
  val ficus                 = "com.iheart"              %% "ficus"                        % FicusVersion

  val kafkastreams          = "org.apache.kafka"        %  "kafka-streams"                % kafkaVersion
  val kafkastreamsScala     = "org.apache.kafka"        %% "kafka-streams-scala"          % kafkaVersion

  val flinkScala            = "org.apache.flink"        % "flink-scala_2.11"              % flinkVersion
  val flinkStreaming        = "org.apache.flink"        % "flink-streaming-scala_2.11"    % flinkVersion
  val flinkKafka            = "org.apache.flink"        %% "flink-connector-kafka-0.11"   % flinkVersion


  val sparkcore             = "org.apache.spark"        % "spark-core_2.11"               % sparkVersion
  val sparkstreaming        = "org.apache.spark"        % "spark-streaming_2.11"          % sparkVersion
  val sparkSQLkafka         = "org.apache.spark"        % "spark-sql-kafka-0-10_2.11"     % sparkVersion
  val sparkSQL              = "org.apache.spark"        % "spark-sql_2.11"                % sparkVersion 
  val sparkcatalyst         = "org.apache.spark"        % "spark-catalyst_2.11"           % sparkVersion
  val sparkkafka            = "org.apache.spark"        % "spark-streaming-kafka-0-10_2.11" % sparkVersion



  val kafkaBaseDependencies = Seq(kafka, curator)
  val akkaServerDependencies = Seq(reactiveKafka, akkaStreamTyped, akkatyped, akkaHttp, akkaHttpJsonJackson, reactiveKafka)
  val kafkaServerDependencies = Seq(kafkastreams, kafkastreamsScala)
  val flinkServerDependencies = Seq(flinkScala, flinkStreaming, flinkKafka)
  val sparkServerDependencies = Seq(sparkcore, sparkstreaming, sparkSQLkafka, sparkSQL, sparkkafka, sparkcatalyst)

  val configDependencies = Seq(typesafeConfig, ficus)
}
