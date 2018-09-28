package com.lightbend.ad.speculativemodelserver.modelserver

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.lightbend.ad.configuration.IntelConfig
import com.lightbend.ad.influx.{InfluxDBSink, ServingData}
import com.lightbend.ad.model.{DataRecord, ModelToServe, ModelWithDescriptor, ServingResult}
import com.lightbend.ad.model.speculative.SpeculativeConverter
import com.lightbend.ad.speculativemodelserver.actors.ModelServingManager
import com.lightbend.ad.speculativemodelserver.queryablestate.QueriesAkkaHttpResource
import com.lightbend.model.cpudata.ServingResultMessage
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer}

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

  val dataConsumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer)
    .withBootstrapServers(kafkaDataConfig.brokers)
    .withGroupId(kafkaDataConfig.sourcegroup)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  val modelConsumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer)
    .withBootstrapServers(kafkaDataConfig.brokers)
    .withGroupId(kafkaDataConfig.modelgroup)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  val speculativeConsumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer)
    .withBootstrapServers(kafkaDataConfig.brokers)
    .withGroupId(kafkaDataConfig.speculativegroup)
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  // Sink
  val producerSettings =
    ProducerSettings(system.settings.config.getConfig("akka.kafka.producer"), new ByteArraySerializer, new ByteArraySerializer)
      .withBootstrapServers(kafkaDataConfig.brokers)

  def main(args: Array[String]): Unit = {

    println(s"Starting Kafka readers for brokers ${kafkaDataConfig.brokers}")
    println(s"Model server input queue ${kafkaDataConfig.sourcetopic}, input group ${kafkaDataConfig.sourcegroup} ")
    println(s"Model server model queue ${kafkaDataConfig.modeltopic}, input group ${kafkaDataConfig.modelgroup} ")
    println(s"Model server serving queue ${kafkaDataConfig.servingtopic}, input group ${kafkaDataConfig.servinggroup} ")

    val modelserver = system.actorOf(ModelServingManager.props)
    val influxDBSink = InfluxDBSink()

    // Speculative stream processing
    Consumer.atMostOnceSource(speculativeConsumerSettings, Subscriptions.topics(kafkaDataConfig.speculativegroup))
      .map(record => SpeculativeConverter.fromByteArray(record.value)).collect { case Success(a) => a }
      .ask[String](1)(modelserver)
      .runWith(Sink.ignore) // run the stream, we do not read the results directly

    // Model stream processing
    Consumer.atMostOnceSource(modelConsumerSettings, Subscriptions.topics(kafkaDataConfig.modeltopic))
      .map(record => ModelToServe.fromByteArray(record.value)).collect { case Success(a) => a }
      .map(record => ModelWithDescriptor.fromModelToServe(record)).collect { case Success(a) => a }
      .ask[String](1)(modelserver)
      .runWith(Sink.ignore) // run the stream, we do not read the results directly

    // Data stream processing
    Consumer.atMostOnceSource(dataConsumerSettings, Subscriptions.topics(kafkaDataConfig.sourcetopic))
      .map(record => DataRecord.fromByteArray(record.value)).collect { case Success(a) => a }
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
    startRest(modelserver)
  }

  def startRest(modelserver: ActorRef): Unit = {

    implicit val timeout = Timeout(10.seconds)
    val host = "0.0.0.0"
    val port = modelServerConfig.port
    val routes: Route = QueriesAkkaHttpResource.storeRoutes(modelserver)

    val _ = Http().bindAndHandle(routes, host, port) map
      { binding =>
          println(s"Starting models observer on port ${binding.localAddress}") } recover {
        case ex =>
          println(s"Models observer could not bind to $host:$port - ${ex.getMessage}")
      }
  }
}
