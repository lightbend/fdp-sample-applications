package com.lightbend.killrweather.client.grpc

import java.io.{ BufferedReader, FileInputStream, InputStreamReader }
import java.util.zip.GZIPInputStream

import com.lightbend.killrweather.settings.WeatherSettings
import com.lightbend.killrweather.utils.RawWeatherData
import com.lightbend.killrweather.WeatherClient.WeatherListenerGrpc.WeatherListenerBlockingStub
import com.lightbend.killrweather.WeatherClient.{ WeatherListenerGrpc, WeatherRecord }

import io.grpc.{ ManagedChannel, ManagedChannelBuilder, StatusRuntimeException }

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

  def convertRecord(string: String): WeatherRecord = {
    val report = RawWeatherData(string.split(","))
    WeatherRecord(
      wsid = report.wsid,
      year = report.year,
      month = report.month,
      day = report.day,
      hour = report.hour,
      temperature = report.temperature,
      dewpoint = report.dewpoint,
      pressure = report.pressure,
      windDirection = report.windDirection,
      windSpeed = report.windSpeed,
      skyCondition = report.skyCondition,
      skyConditionText = report.skyConditionText,
      oneHourPrecip = report.oneHourPrecip,
      sixHourPrecip = report.sixHourPrecip
    )
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
        val response = blockingStub.getWeatherReport(convertRecord(record))
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

class BufferedReaderIterator(reader: BufferedReader) extends Iterator[String] {
  override def hasNext() = reader.ready()
  override def next() = reader.readLine()
}

object GzFileIterator {
  def apply(file: java.io.File, encoding: String) = {
    new BufferedReaderIterator(
      new BufferedReader(
        new InputStreamReader(
          new GZIPInputStream(
            new FileInputStream(file)
          ), encoding
        )
      )
    )
  }
}