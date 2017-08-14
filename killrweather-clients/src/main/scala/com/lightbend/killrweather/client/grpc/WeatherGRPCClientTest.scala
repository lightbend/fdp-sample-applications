package com.lightbend.killrweather.client.grpc

import java.util.concurrent.TimeUnit

import com.lightbend.killrweather.WeatherClient.WeatherListenerGrpc.WeatherListenerBlockingStub
import com.lightbend.killrweather.WeatherClient.{WeatherListenerGrpc, WeatherRecord}
import com.lightbend.killrweather.utils.RawWeatherData
import io.grpc.{ManagedChannel, ManagedChannelBuilder, StatusRuntimeException}

object WeatherGRPCClientTest {

  def apply(host: String, port: Int): WeatherGRPCClientTest = {
    val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build
    val blockingStub = WeatherListenerGrpc.blockingStub(channel)
    new WeatherGRPCClientTest(channel, blockingStub)
  }

  def main(args: Array[String]): Unit = {
    val client = WeatherGRPCClientTest("localhost", 50051)
    try {
      val report = RawWeatherData("725030:14732", 2008, 1, 1, 0, 5.0, -3.9, 1020.4, 270, 4.6, 0, "2", 0.0, 0.0)
      client.send(report)
    } finally {
      client.shutdown()
    }
  }
}


class WeatherGRPCClientTest(private val channel: ManagedChannel,
                                   private val blockingStub: WeatherListenerBlockingStub) {

  def shutdown(): Unit = {
    val result = channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
    println(s"WeatherGRPCAsynchClientTest.shutdown: ManagedChannel terminated? $result")
  }

  /** Send report to a server. */
  def send(report: RawWeatherData): Unit = {
    val request = WeatherRecord(
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
    try {
      val response = blockingStub.getWeatherReport(request)
      println(s"Report send:  ${response.status}")
    }
    catch {
      case e: StatusRuntimeException =>
        println(s"RPC failed: ${e.getStatus}")
    }
  }
}
