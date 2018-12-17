package com.lightbend.fdp.sample.flink.config

import java.nio.charset.Charset

import cats.data._
import cats.instances.all._

import scala.util.Try
import com.typesafe.config.Config

import scala.concurrent.duration._

/**
 * This object wraps the native Java config APIs into a monadic
 * interpreter
 */
object TaxiRideConfig {
  final val CHARSET = Charset.forName("UTF-8")

  implicit def asFiniteDuration(d: java.time.Duration) =
    scala.concurrent.duration.Duration.fromNanos(d.toNanos)

  private[TaxiRideConfig] case class KafkaSettings(
    brokers: String,
    inTopic: String,
    outTopic: String
  )

  private[TaxiRideConfig] case class DataLoaderSettings(
    sourceTopic: String,
    directoryToWatch: String,
    pollInterval: FiniteDuration
  )

  case class ConfigData(ks: KafkaSettings, dls: DataLoaderSettings) {
    def brokers = ks.brokers
    def inTopic = ks.inTopic
    def outTopic = ks.outTopic
    def sourceTopic = dls.sourceTopic
    def directoryToWatch = dls.directoryToWatch
    def pollInterval = dls.pollInterval
  }

  type ConfigReader[A] = ReaderT[Try, Config, A]

  private def fromKafkaConfig: ConfigReader[KafkaSettings] = Kleisli { (config: Config) =>
    Try {
      KafkaSettings(
        config.getString("dcos.kafka.brokers"),
        config.getString("dcos.kafka.intopic"),
        config.getString("dcos.kafka.outtopic")
      )
    }
  }

  private def fromDataLoaderConfig: ConfigReader[DataLoaderSettings] = Kleisli { (config: Config) =>
    Try {
      DataLoaderSettings(
        config.getString("dcos.kafka.loader.sourcetopic"),
        config.getString("dcos.kafka.loader.directorytowatch"),
        config.getDuration("dcos.kafka.loader.pollinterval")
      )
    }
  }

  def fromConfig: ConfigReader[ConfigData] = for {
    k <- fromKafkaConfig
    d <- fromDataLoaderConfig
  } yield ConfigData(k, d)
}
