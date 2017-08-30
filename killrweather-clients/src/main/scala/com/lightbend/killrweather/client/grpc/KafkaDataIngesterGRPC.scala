package com.lightbend.killrweather.client.grpc

import com.lightbend.killrweather.settings.WeatherSettings
import com.lightbend.killrweather.WeatherClient.WeatherListenerGrpc.WeatherListenerBlockingStub
import com.lightbend.killrweather.WeatherClient.{WeatherListenerGrpc}
import com.lightbend.killrweather.client.utils.{DataConvertor, GzFileIterator}
import io.grpc.{ManagedChannel, ManagedChannelBuilder, StatusRuntimeException}

/**
 * Created by boris on 7/7/17.
 */
object KafkaDataIngesterGRPC {

  val file = "data/load/ny-2008.csv.gz"
  val timeInterval: Long = 100 * 1 // 1 sec
  val batchSize = 10

  def main(args: Array[String]) {

    val settings = new WeatherSettings()
    //    val host = "localhost"
    val host = "10.8.0.21"
    val port = 50051

    val ingester = KafkaDataIngesterGRPC(host, port)
    ingester.execute(file)
  }

  def apply(host: String, port: Int): KafkaDataIngesterGRPC = {
    val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build
    val blockingStub = WeatherListenerGrpc.blockingStub(channel)
    new KafkaDataIngesterGRPC(channel, blockingStub)
  }
}

class KafkaDataIngesterGRPC(
    private val channel: ManagedChannel,
    private val blockingStub: WeatherListenerBlockingStub
) {

  import KafkaDataIngesterGRPC._

  def execute(file: String): Unit = {

    val iterator = GzFileIterator(new java.io.File(file), "UTF-8")
    var numrec = 0;
    iterator.foreach(record => {
      try {
        val response = blockingStub.getWeatherReport(DataConvertor.convertToRecord(record))
      } catch {
        case e: StatusRuntimeException =>
          println(s"RPC failed: ${e.getStatus}")
      }
      numrec = numrec + 1
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