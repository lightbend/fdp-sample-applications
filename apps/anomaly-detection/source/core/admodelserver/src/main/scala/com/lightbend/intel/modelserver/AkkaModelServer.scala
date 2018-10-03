package com.lightbend.ad.modelserver

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.kafka.scaladsl.Producer
import akka.util.Timeout
import com.lightbend.ad.influx.{InfluxDBSink, ServingData}
import com.lightbend.ad.modelserver.actors.ModelServingManager
import com.lightbend.model.cpudata.{CPUData, ServingResultMessage}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer}
import com.typesafe.config.ConfigFactory
import com.lightbend.ad.configuration.IntelConfig
import com.lightbend.ad.model.{DataRecord, ModelToServe, ModelWithDescriptor, ServingResult}
import com.lightbend.ad.modelserver.queryablestate.RestService

import scala.concurrent.duration._
import scala.util.Success

/**
 * Created by boris on 7/21/17.
 */
object AkkaModelServer {

  implicit val system = ActorSystem("ModelServing")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val askTimeout = Timeout(30.seconds)

  // Create context
  val settings = IntelConfig.fromConfig(ConfigFactory.load()).get
  import settings._

  // Sources
  val dataConsumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer)
    .withBootstrapServers(kafkaDataConfig.brokers)
    .withGroupId(kafkaDataConfig.sourcegroup)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  val modelConsumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer)
    .withBootstrapServers(kafkaDataConfig.brokers)
    .withGroupId(kafkaDataConfig.modelgroup)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  // Sink
  val producerSettings =
    ProducerSettings(system.settings.config.getConfig("akka.kafka.producer"), new ByteArraySerializer, new ByteArraySerializer)
      .withBootstrapServers(kafkaDataConfig.brokers)


  def main(args: Array[String]): Unit = {

    println(s"Model server Using kafka brokers at ${kafkaDataConfig.brokers} ")
    println(s"Model server input queue ${kafkaDataConfig.sourcetopic}, input group ${kafkaDataConfig.sourcegroup} ")
    println(s"Model server model queue ${kafkaDataConfig.modeltopic}, input group ${kafkaDataConfig.modelgroup} ")
    println(s"Model server serving queue ${kafkaDataConfig.servingtopic}, input group ${kafkaDataConfig.servinggroup} ")

    val modelserver = system.actorOf(ModelServingManager.props)
    val influxDBSink = InfluxDBSink()

    // Model Stream
    Consumer.atMostOnceSource(modelConsumerSettings, Subscriptions.topics(kafkaDataConfig.modeltopic))
      .map(record => ModelToServe.fromByteArray(record.value())).collect { case Success(mts) => mts }
      .map(record => ModelWithDescriptor.fromModelToServe(record)).collect { case Success(mod) => mod }
      .ask[String](1)(modelserver)
      .runWith(Sink.ignore)

    // Data stream processing
    Consumer.atMostOnceSource(dataConsumerSettings, Subscriptions.topics(kafkaDataConfig.sourcetopic))
      .map(record => DataRecord.fromByteArray(record.value)).collect { case Success(dataRecord) => dataRecord }
      .ask[ServingResult](1)(modelserver)
      .map(result => {
        result.processed match {
          case true =>
            result.result match{
              case Some(r) =>
                println(s"Using model ${result.model}. Calculated result - $r, expected ${result.source} calculated in ${result.duration} ms")
                influxDBSink.write(new ServingData(r, result.source, result.model, result.duration))
                Some(new ServingResultMessage(r))
              case _ => None
            }
          case _ =>
            println("No model available - skipping")
            None
        }

      })
      .filter(_.isDefined)
      .map(value => new ProducerRecord[Array[Byte], Array[Byte]](kafkaDataConfig.servingtopic, DataRecord.toByteArray(value.get)))
      .runWith(Producer.plainSink(producerSettings))

    // Rest Server
    RestService.startRest(modelserver)
  }
}