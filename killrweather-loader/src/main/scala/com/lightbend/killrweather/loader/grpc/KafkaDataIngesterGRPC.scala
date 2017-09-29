package com.lightbend.killrweather.loader.grpc

import com.lightbend.killrweather.settings.WeatherSettings
import com.lightbend.killrweather.WeatherClient.{ WeatherListenerGrpc, WeatherRecord }
import com.lightbend.killrweather.loader.utils.{ DataConvertor, FilesIterator }
import io.grpc.ManagedChannelBuilder

/**
 * Created by boris on 7/7/17.
 */
object KafkaDataIngesterGRPC {

  val file = "data/load"
  val timeInterval: Long = 100 * 1 // 1 sec
  val batchSize = 10

  def main(args: Array[String]) {

    // See --help, which describes the --grpc-host option for specifying the host:port
    WeatherSettings.handleArgs("WeatherGRPCClient", args)

    val settings = new WeatherSettings()

    //    val host = sys.props.getOrElse("grpc.ingester.client.host", "localhost") // "10.8.0.16"
//    val host = "10.8.0.16"
    val host = "10.2.2.221"
    val port = sys.props.getOrElse("grpc.ingester.client.port", "50051").toInt

    val ingester = new KafkaDataIngesterGRPC(host, port)
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
