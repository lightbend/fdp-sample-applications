package com.lightbend.killrweather.client.kafkatest

import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, Subscriptions}
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import com.lightbend.killrweather.settings.WeatherSettings
import org.apache.kafka.clients.consumer.ConsumerConfig
import akka.kafka.scaladsl.Consumer
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Sink
import com.lightbend.killrweather.Record.WeatherRecord
// import com.lightbend.killrweather.kafka.EmbeddedSingleNodeKafkaCluster

import scala.concurrent.Future

/**
  * Created by boris on 7/7/17.
  * This is a simple kafka listener to test messages send to kafka
  */
object KafkaListener {

  def main(args: Array[String]) {

    implicit val system = ActorSystem("SimpleKafkaListenr")
    implicit val materializer = ActorMaterializer()

    implicit val executionContext = system.dispatcher
    val settings = new WeatherSettings()

    import settings._

    // Create embedded Kafka and topic
//    EmbeddedSingleNodeKafkaCluster.start()
//    EmbeddedSingleNodeKafkaCluster.createTopic(KafkaTopicRaw)

    val consumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers("localhost:9092")
      .withGroupId(KafkaGroupId)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    val done = Consumer.plainSource(consumerSettings, Subscriptions.topics(KafkaTopicRaw))
      .mapAsync(1){record => Future(println(WeatherRecord.parseFrom(record.value())))}.runWith(Sink.ignore)
  }
}
