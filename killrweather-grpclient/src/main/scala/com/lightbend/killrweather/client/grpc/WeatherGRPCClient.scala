package com.lightbend.killrweather.client.grpc

import java.io.ByteArrayOutputStream

import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.lightbend.killrweather.WeatherClient.WeatherListenerGrpc.WeatherListener
import com.lightbend.killrweather.WeatherClient.{ Reply, WeatherListenerGrpc, WeatherRecord }
import com.lightbend.killrweather.settings.WeatherSettings
import io.grpc.{ Server, ServerBuilder }
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer

import scala.concurrent.{ ExecutionContext, Future }

object WeatherGRPCClient {

  implicit val system = ActorSystem("WeatherDataIngester")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  private val port = 50051
  private val bos = new ByteArrayOutputStream()

  // Ugly...
  def producerSettings: ProducerSettings[Array[Byte], Array[Byte]] = _producerSettings
  private var _producerSettings: ProducerSettings[Array[Byte], Array[Byte]] = _

  def main(args: Array[String]): Unit = {

    val settings = WeatherSettings("WeatherGRPCClient", args)
    import settings._

    println(s"Running GRPC Client. Kafka: $kafkaBrokers")
    _producerSettings = ProducerSettings(system, new ByteArraySerializer, new ByteArraySerializer)
      .withBootstrapServers(kafkaConfig.brokers)

    val server = WeatherGRPCClient(kafkaConfig.topic)
    server.start()
    server.blockUntilShutdown()

  }

  def convertRecord(report: WeatherRecord): Array[Byte] = {
    bos.reset
    report.writeTo(bos)
    bos.toByteArray
  }

  def apply(topic: String): WeatherGRPCClient = new WeatherGRPCClient(topic)
}

class WeatherGRPCClient(topic: String)(implicit executionContext: ExecutionContext, materializer: ActorMaterializer, system: ActorSystem) {

  private[this] var server: Server = null

  private def start(): Unit = {
    server = ServerBuilder.forPort(WeatherGRPCClient.port).addService(WeatherListenerGrpc
      .bindService(new WeatherListenerImpl(topic), executionContext)).build.start
    print(s"Server started, listening on  ${WeatherGRPCClient.port}")
    val hook = sys.addShutdownHook {
      System.err.println("*** shutting down gRPC server since JVM is shutting down")
      stop()
      System.err.println("*** server shut down")
    }
  }

  private def stop(): Unit = {
    if (server != null) {
      val result = server.shutdown()
    }
  }

  private def blockUntilShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }
}

class WeatherListenerImpl(topic: String)(implicit executionContext: ExecutionContext, materializer: ActorMaterializer, system: ActorSystem) extends WeatherListener {

  override def getWeatherReport(report: WeatherRecord): Future[Reply] = {

    Source.single(report).map { r =>
      //      print(s"New report $r")
      new ProducerRecord[Array[Byte], Array[Byte]](topic, WeatherGRPCClient.convertRecord(r))
    }
      .runWith(Producer.plainSink(WeatherGRPCClient.producerSettings))
    Future.successful(Reply(true))
  }
}
