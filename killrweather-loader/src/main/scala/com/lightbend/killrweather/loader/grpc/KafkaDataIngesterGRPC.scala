package com.lightbend.killrweather.loader.grpc

import com.lightbend.killrweather.settings.WeatherSettings
import com.lightbend.killrweather.WeatherClient.{WeatherListenerGrpc, WeatherRecord}
import com.lightbend.killrweather.loader.utils.{DataConvertor, FilesIterator}
import io.grpc.{ManagedChannel, ManagedChannelBuilder, StatusRuntimeException}

/**
 * Created by boris on 7/7/17.
 */
object KafkaDataIngesterGRPC {

  val file = "data/load"
  val timeInterval: Long = 100 * 1 // 1 sec
  val batchSize = 10

  def main(args: Array[String]) {

    val settings = new WeatherSettings()
    //    val host = "localhost"
    val host = "10.8.0.20"
    val port = 50051

    val ingester = new KafkaDataIngesterGRPC(host, port)
    ingester.execute(file)
  }
}

class KafkaDataIngesterGRPC(host: String, port: Int) {

  import KafkaDataIngesterGRPC._

  def execute(file: String): Unit = {

    val sender = new GRPCSender(host, port)
    val iterator = FilesIterator(new java.io.File(file), "UTF-8")
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

  private def pause(): Unit = {
    try {
      Thread.sleep(timeInterval)
    } catch {
      case _: Throwable => // Ignore
    }
  }
}

class GRPCSender(host: String, port: Int) {

  private val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build
  private val blockingStub = WeatherListenerGrpc.blockingStub(channel)

  def send(record : WeatherRecord) : Unit = {
    try {
      val response = blockingStub.getWeatherReport(record)
    } catch {
      case e: StatusRuntimeException =>
        println(s"RPC failed: ${e.getStatus}")
    }
  }
}