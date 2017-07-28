package com.lightbend.killrweather.client.grpc

import java.util.concurrent.TimeUnit

import com.lightbend.killrweather.WeatherClient.WeatherListenerGrpc.WeatherListenerStub
import com.lightbend.killrweather.WeatherClient.{WeatherListenerGrpc, WeatherRecord}
import com.lightbend.killrweather.utils.RawWeatherData
import io.grpc.{ManagedChannel, ManagedChannelBuilder, StatusRuntimeException}

import scala.concurrent.ExecutionContext

object WeatherGRPCAsynchClientTest {

  def apply(host: String, port: Int): WeatherGRPCAsynchClientTest = {
    val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build
    val asynchStub = WeatherListenerGrpc.stub(channel)
    new WeatherGRPCAsynchClientTest(channel, asynchStub)
  }

  def main(args: Array[String]): Unit = {
    implicit val executionContext = ExecutionContext.global
    val client = WeatherGRPCAsynchClientTest("localhost", 50051)
    try {
      val report = RawWeatherData("725030:14732", 2008, 1, 1, 0, 5.0, -3.9, 1020.4, 270, 4.6, 0, "2", 0.0, 0.0)
      client.send(report)
    } finally {
      // Sleep to make sure that call completes
      Thread.sleep(1000)
      client.shutdown()
    }
  }
}


class WeatherGRPCAsynchClientTest(private val channel: ManagedChannel,
                            private val asynchStub: WeatherListenerStub) {

  def shutdown(): Unit = {
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }

  /** Send report to a server. */
  def send(report: RawWeatherData) (implicit executionContext: ExecutionContext): Unit = {
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
      val response = asynchStub.getWeatherReport(request)
      response onComplete println
    }
    catch {
      case e: StatusRuntimeException =>
        println(s"RPC failed: ${e.getStatus}")
    }
  }
}
