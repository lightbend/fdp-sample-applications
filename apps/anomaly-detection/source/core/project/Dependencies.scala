/**
  * Created by boris on 7/14/17.
  */
import Versions._
import sbt._

object Dependencies {
  val reactiveKafka = "com.typesafe.akka"               % "akka-stream-kafka_2.11"        % reactiveKafkaVersion
  
  val akkaStream    = "com.typesafe.akka"               % "akka-stream_2.11"              % akkaVersion
  val akkaHttp      = "com.typesafe.akka"               % "akka-http_2.11"                % akkaHttpVersion
  val akkaHttpJsonJackson = "de.heikoseeberger"         % "akka-http-jackson_2.11"        % akkaHttpJsonVersion


  val kafka         = "org.apache.kafka"                % "kafka_2.11"                    % kafkaVersion

//  val curator       = "org.apache.curator"              % "curator-test"                  % Curator                 // ApacheV2


  val tensorflow    = "org.tensorflow"                  % "tensorflow"                    % tensorflowVersion


  val influxDBClient    = "org.influxdb"                % "influxdb-java"                 % influxDBClientVersion
  val scalaHTTP         = "org.scalaj"                  %  "scalaj-http_2.11"             % ScalaHTTPVersion

  val typesafeConfig    = "com.typesafe"                %  "config"                       % TypesafeConfigVersion
  val ficus             = "com.iheart"                  %% "ficus"                        % FicusVersion

  val cats              = "org.typelevel"               %% "cats-core"                    % catsVersion
  val catsEffect        = "org.typelevel"               %% "cats-effect"                  % catsEffectVersion
  val monix             = "io.monix"                    %% "monix"                        % monixVersion
  val guava             = "com.google.guava"            %  "guava"                        % guavaVersion
  


  val modelsDependencies    = Seq(tensorflow)
  val kafkaBaseDependencies = Seq(kafka, reactiveKafka)
  val akkaServerDependencies = Seq(reactiveKafka, akkaStream, akkaHttp, akkaHttpJsonJackson, reactiveKafka)

  val configDependencies = Seq(typesafeConfig, ficus)

}
