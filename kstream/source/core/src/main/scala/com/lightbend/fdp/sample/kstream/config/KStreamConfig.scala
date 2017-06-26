package com.lightbend.fdp.sample.kstream
package config

import cats._
import cats.data._
import cats.instances.all._

import scala.util.Try
import com.typesafe.config.Config

/**
 * This object wraps the native Java config APIs into a monadic
 * interpreter
 */ 
object KStreamConfig {

  private[KStreamConfig] case class KafkaSettings(
    brokers: String, 
    zk: String, 
    fromTopic: String, 
    toTopic: String, 
    summaryAccessTopic: String, 
    summaryPayloadTopic: String, 
    errorTopic: String
  )

  case class ConfigData(ks: KafkaSettings) {
    def brokers = ks.brokers
    def zk = ks.zk
    def fromTopic = ks.fromTopic
    def toTopic = ks.toTopic
    def summaryAccessTopic = ks.summaryAccessTopic
    def summaryPayloadTopic = ks.summaryPayloadTopic
    def errorTopic = ks.errorTopic
  }

  type ConfigReader[A] = ReaderT[Try, Config, A]

  private def fromKafkaConfig: ConfigReader[KafkaSettings] = Kleisli { (config: Config) =>
    Try {
      KafkaSettings(
        config.getString("dcos.kafka.brokers"),
        config.getString("dcos.kafka.zookeeper"),
        config.getString("dcos.kafka.fromtopic"),
        config.getString("dcos.kafka.totopic"),
        config.getString("dcos.kafka.summaryaccesstopic"),
        config.getString("dcos.kafka.summarypayloadtopic"),
        config.getString("dcos.kafka.errortopic")
      )
    }
  }

  def fromConfig: ConfigReader[ConfigData] = for {
    k <- fromKafkaConfig
  } yield ConfigData(k)
}

