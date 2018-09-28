package com.lightbend.ad.configuration

import com.typesafe.config.{Config, ConfigFactory}

import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.util.Try
import scala.concurrent.duration.FiniteDuration

import cats.data._
import cats.instances.all._

object IntelConfig {
  private [IntelConfig] case class KafkaConfig(brokers: String = "broker.kafka.l4lb.thisdcos.directory:9092",
                         sourcetopic: String = "intelData", sourcegroup: String = "IntelDataGroup",
                         modeltopic: String = "intelModel", modelgroup: String = "IntelModelGroup",
                         servingtopic: String = "intelServing", servinggroup: String = "IntelServingGroup",
                         speculativetopic: String = "intelspeculative", speculativegroup: String = "IntelSpeculativeGroup")

  private [IntelConfig] case class LoaderConfig(publishinterval : String = "1 second", model : String = "data/model.pb")
  
  private [IntelConfig] case class ModelServerConfig(port : Int = 5500)
  
  private [IntelConfig] case class InfluxDBConfig(host: String = "influxdb.marathon.l4lb.thisdcos.directory", port: Int = 8086, 
    user: String = "root", password: String = "root") {
    def url = s"http://$host:$port"
  }
  
  private [IntelConfig] case class InfluxTableConfig(database: String="intelDemo", retentionPolicy: String="dayly")
  
  private [IntelConfig] case class GrafanaConfig(host: String = "grafana.marathon.l4lb.thisdcos.directory", port: Int = 3000, 
    user: String = "admin", password: String = "admin") {
    def url = s"http://$host:$port"
  }

  private [IntelConfig] case class IngesterConfig(ingestInterval: FiniteDuration = java.time.Duration.ofMinutes(30), 
    dataFileName: String = "data/CP_examples.csv", generationCompleteFileName: String = "/tmp/data_reparation_complete.txt",
    lastTimestampFileName: String = "data/last_timestamp.txt", ingestThresholdCount: Int = 256)

  private [IntelConfig] case class ModelConfig(pbFileName: String = "/tmp/model.pb", 
    attributesFileName: String = "/tmp/model-attrib.properties", hyperparamsFileName: String = "/tmp/hyperparams.properties")

  type ConfigReader[A] = ReaderT[Try, Config, A]

  private def fromKafkaSettings: ConfigReader[KafkaConfig] = Kleisli { (config: Config) =>
    Try(config.as[KafkaConfig]("kafka")).recover { case _: Throwable => KafkaConfig() }
  }

  private def fromLoaderSettings: ConfigReader[LoaderConfig] = Kleisli { (config: Config) =>
    Try(config.as[LoaderConfig]("loader")).recover { case _: Throwable => LoaderConfig() }
  }

  private def fromInfluxDBSettings: ConfigReader[InfluxDBConfig] = Kleisli { (config: Config) =>
    Try(config.as[InfluxDBConfig]("influxdb")).recover { case _: Throwable => InfluxDBConfig() }
  }

  private def fromInfluxTableSettings: ConfigReader[InfluxTableConfig] = Kleisli { (config: Config) =>
    Try(config.as[InfluxTableConfig]("influxTable")).recover { case _: Throwable => InfluxTableConfig() }
  }

  private def fromGrafanaSettings: ConfigReader[GrafanaConfig] = Kleisli { (config: Config) =>
    Try(config.as[GrafanaConfig]("grafana")).recover { case _: Throwable => GrafanaConfig() }
  }

  private def fromModelServerSettings: ConfigReader[ModelServerConfig] = Kleisli { (config: Config) =>
    Try(config.as[ModelServerConfig]("modelServer")).recover { case _: Throwable => ModelServerConfig() }
  }

  private def fromIngesterSettings: ConfigReader[IngesterConfig] = Kleisli { (config: Config) =>
    Try(config.as[IngesterConfig]("ingester")).recover { case _: Throwable => IngesterConfig() }
  }

  private def fromModelSettings: ConfigReader[ModelConfig] = Kleisli { (config: Config) =>
    Try(config.as[ModelConfig]("model")).recover { case _: Throwable => ModelConfig() }
  }

  case class IntelSettings(val kafkaDataConfig: KafkaConfig,
                           val loaderConfig: LoaderConfig,
                           val influxConfig: InfluxDBConfig,
                           val influxTableConfig: InfluxTableConfig,
                           val grafanaConfig: GrafanaConfig,
                           val modelServerConfig: ModelServerConfig,
                           val ingesterConfig: IngesterConfig,
                           val modelConfig: ModelConfig) extends Serializable

  def fromConfig: ConfigReader[IntelSettings] = for {

    kafkaConfig        <- fromKafkaSettings
    loaderConfig       <- fromLoaderSettings
    influxDBConfig     <- fromInfluxDBSettings
    influxTableConfig  <- fromInfluxTableSettings
    grafanaConfig      <- fromGrafanaSettings
    modelServerConfig  <- fromModelServerSettings
    ingesterConfig     <- fromIngesterSettings
    modelConfig        <- fromModelSettings

  } yield IntelSettings(kafkaConfig, loaderConfig, influxDBConfig, influxTableConfig, 
                        grafanaConfig, modelServerConfig, ingesterConfig, modelConfig)
}
