package com.lightbend.killrweater.beam.influxdb

import java.util.concurrent.TimeUnit

import com.lightbend.killrweater.beam.data.{DailyWeatherData, MonthlyWeatherData, RawWeatherData}
import org.apache.beam.sdk.values.KV
import org.influxdb.dto.Point

object DataTransformers {

  def getRawPoint(record: KV[String, RawWeatherData]): Point = {
    val rawPoint = Point.measurement("raw_weather").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
    rawPoint.addField("year", record.getValue.year.toLong)
    rawPoint.addField("month", record.getValue.month.toLong)
    rawPoint.addField("day", record.getValue.day.toLong)
    rawPoint.addField("hour", record.getValue.hour.toLong)
    rawPoint.addField("temperature", record.getValue.temperature)
    rawPoint.addField("dewpoint", record.getValue.dewpoint)
    rawPoint.addField("pressure", record.getValue.pressure)
    rawPoint.addField("windDirection", record.getValue.windDirection.toLong)
    rawPoint.addField("windSpeed", record.getValue.windSpeed)
    rawPoint.addField("skyConditions", record.getValue.skyCondition.toLong)
    rawPoint.tag("station", record.getKey)
    rawPoint.build()
  }

  def getDaylyPoint(record: KV[String, DailyWeatherData]): Point = {
    val dailyPoint = Point.measurement("daily_temp_weather").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
    dailyPoint.addField("year", record.getValue.year.toLong)
    dailyPoint.addField("month", record.getValue.month.toLong)
    dailyPoint.addField("day", record.getValue.day.toLong)
    dailyPoint.addField("high", record.getValue.highTemp)
    dailyPoint.addField("low", record.getValue.lowTemp)
    dailyPoint.addField("mean", record.getValue.meanTemp)
    dailyPoint.addField("variance", record.getValue.varianceTemp)
    dailyPoint.addField("stdev", record.getValue.stdevTemp)
    dailyPoint.tag("station", record.getKey)
    dailyPoint.build()
  }

  def getMonthlyPoint(record: KV[String, MonthlyWeatherData]): Point = {
    val monthlyPoint = Point.measurement("daily_temp_weather").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
    monthlyPoint.addField("year", record.getValue.year.toLong)
    monthlyPoint.addField("month", record.getValue.month.toLong)
    monthlyPoint.addField("high", record.getValue.highTemp)
    monthlyPoint.addField("low", record.getValue.lowTemp)
    monthlyPoint.addField("mean", record.getValue.meanTemp)
    monthlyPoint.addField("variance", record.getValue.varianceTemp)
    monthlyPoint.addField("stdev", record.getValue.stdevTemp)
    monthlyPoint.tag("station", record.getKey)
    monthlyPoint.build()
  }
}