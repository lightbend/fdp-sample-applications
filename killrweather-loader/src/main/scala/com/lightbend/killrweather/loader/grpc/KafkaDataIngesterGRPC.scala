package com.lightbend.killrweather.loader.grpc

import com.lightbend.killrweather.settings.WeatherSettings
import com.lightbend.killrweather.WeatherClient.{ WeatherListenerGrpc, WeatherRecord }
import com.lightbend.killrweather.loader.utils.{ DataConvertor, FilesIterator }
import io.grpc.ManagedChannelBuilder
import scala.concurrent.duration._

object KafkaDataIngesterGRPC {

  val file = "data/load"
  val timeInterval: Long = 1.second.toMillis
  val batchSize = 10

  def main(args: Array[String]) {

    val settings = WeatherSettings("WeatherGRPCClient", args)

    val grpcConfig = settings.grpcConfig

    val ingester = new KafkaDataIngesterGRPC(grpcConfig.host, grpcConfig.port)
    ingester.execute(file)
  }

  def pause(): Unit = {
    try {
      Thread.sleep(timeInterval)
    } catch {
      case _: Throwable => // Ignore
    }
  }
}

class KafkaDataIngesterGRPC(host: String, port: Int) {

  import KafkaDataIngesterGRPC._

  def execute(file: String): Unit = {

    val sender = new GRPCSender(host, port)
    val iterator = FilesIterator(new java.io.File(file))
    var numrec = 0
    iterator.foreach(record => {
      sender.send(DataConvertor.convertToRecord(record))
      numrec += 1
      if (numrec >= batchSize)
        pause()
      if (numrec % 100 == 0)
        println(s"Submitted $numrec records")
    })
    println(s"Submitted $numrec records")
  }
}

class GRPCSender(host: String, port: Int) {

  private val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build
  private val blockingStub = WeatherListenerGrpc.blockingStub(channel)

  def send(record: WeatherRecord): Unit = {
    try {
      val result = blockingStub.getWeatherReport(record)
    } catch {
      case e: Throwable =>
        println(s"RPC failed: ${e.printStackTrace()}")
        KafkaDataIngesterGRPC.pause()
    }
  }
}
