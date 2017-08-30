package com.lightbend.killrweather.client.utils

import java.io.ByteArrayOutputStream

import com.lightbend.killrweather.WeatherClient.WeatherRecord
import com.lightbend.killrweather.utils.RawWeatherData
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization.write

object DataConvertor {

  implicit val formats = DefaultFormats
  private val bos = new ByteArrayOutputStream()

  def convertToJson(string: String): String = {
    val report = RawWeatherData(string.split(","))
    write(report)
  }

  def convertToRecord(string: String): WeatherRecord = {
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

  def convertToGPB(string: String): Array[Byte] = {
    bos.reset
    convertToRecord(string).writeTo(bos)
    bos.toByteArray
  }
}
