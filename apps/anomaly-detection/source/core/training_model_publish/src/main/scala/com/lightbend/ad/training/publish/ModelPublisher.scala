package com.lightbend.ad.training.publish

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.RecordMetadata

import com.lightbend.ad.kafka._

import ModelUtils._
import com.lightbend.ad.configuration.IntelConfig
import IntelConfig._

object ModelPublisher {

  def publishToKafka(configData: IntelSettings, sender: KafkaMessageSender): IO[RecordMetadata] = {

    import configData._
    for {

      md        <- readModel(modelConfig.pbFileName, modelConfig.attributesFileName, modelConfig.hyperparamsFileName)
      metadata  <- publishModelToKafka(sender, md, kafkaDataConfig.modeltopic)

    } yield metadata
  }

  def main(args: Array[String]): Unit = {

    // get config
    val configData = fromConfig(ConfigFactory.load()).get
    println(s"Starting publishing service with config: $configData")

    // get kafka brokers from config
    val kafkaBrokers = configData.kafkaDataConfig.brokers
    println(s"Kafka brokers found: $kafkaBrokers")

    // make a Kafka sender
    val sender = new KafkaMessageSender(kafkaBrokers)

    // publish to Kafka
    val metadata = publishToKafka(configData, sender).unsafeRunSync
    println(s"Metadata from Kafka [topic: ${metadata.topic}, timestamp: ${metadata.timestamp}]")

    ()
  }
}


