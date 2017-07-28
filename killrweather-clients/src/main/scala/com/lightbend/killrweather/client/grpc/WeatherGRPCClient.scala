package com.lightbend.killrweather.client.grpc

import java.io.ByteArrayOutputStream

import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.lightbend.killrweather.WeatherClient.WeatherListenerGrpc.WeatherListener
import com.lightbend.killrweather.WeatherClient.{Reply, WeatherListenerGrpc, WeatherRecord}
import com.lightbend.killrweather.settings.WeatherSettings
import io.grpc.{Server, ServerBuilder}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object WeatherGRPCClient{

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("WeatherDataIngester")
    implicit val materializer = ActorMaterializer()


    implicit val executionContext = system.dispatcher
    implicit val timeout = Timeout(10 seconds)


    val server = new WeatherGRPCClient()
    server.start()
    server.blockUntilShutdown()
  }

  def convertRecord(report: WeatherRecord) : Array[Byte] = {
    bos.reset
    report.writeTo(bos)
    bos.toByteArray
  }

  private val port = 50051
  private val bos = new ByteArrayOutputStream()


}

class WeatherGRPCClient(implicit executionContext: ExecutionContext, materializer : ActorMaterializer, system : ActorSystem) {

  private[this] var server: Server = null

  private def start(): Unit = {
    server = ServerBuilder.forPort(WeatherGRPCClient.port).addService(WeatherListenerGrpc
      .bindService(new WeatherListenerImpl, executionContext)).build.start
    print(s"Server started, listening on  ${WeatherGRPCClient.port}")
    sys.addShutdownHook {
      System.err.println("*** shutting down gRPC server since JVM is shutting down")
      stop()
      System.err.println("*** server shut down")
    }
  }

  private def stop(): Unit = {
    if (server != null) {
      server.shutdown()
    }
  }

  private def blockUntilShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }
}

class WeatherListenerImpl(implicit executionContext: ExecutionContext, materializer : ActorMaterializer, system : ActorSystem) extends WeatherListener{

  val settings = new WeatherSettings()
  import settings._

  val producerSettings = ProducerSettings(system, new ByteArraySerializer, new ByteArraySerializer)
    .withBootstrapServers(kafkaBrokers)

  override def getWeatherReport(report: WeatherRecord) : Future[Reply] = {

    Source.single(report).map { r =>
      print(s"New report $r")
      new ProducerRecord[Array[Byte], Array[Byte]](KafkaTopicRaw, WeatherGRPCClient.convertRecord(r))}
      .runWith(Producer.plainSink(producerSettings))
    Future.successful(Reply(true))
  }
}