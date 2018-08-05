package com.lightbend.killrweather.utils

/**
 * Created by boris on 7/19/17.
 */
case class DailyTemperature(
  wsid: String,
  year: Int,
  month: Int,
  day: Int,
  high: Double,
  low: Double,
  mean: Double,
  variance: Double,
  stdev: Double
) extends Serializable
object DailyTemperature {
  def apply(daily: DailyWeatherData): DailyTemperature =
    new DailyTemperature(daily.wsid, daily.year, daily.month, daily.day, daily.highTemp, daily.lowTemp,
      daily.meanTemp, daily.varianceTemp, daily.stdevTemp)
}

case class MonthlyTemperature(
  wsid: String,
  year: Int,
  month: Int,
  high: Double,
  low: Double,
  mean: Double,
  variance: Double,
  stdev: Double
) extends Serializable
object MonthlyTemperature {
  def apply(monthly: MonthlyWeatherData): MonthlyTemperature =
    new MonthlyTemperature(monthly.wsid, monthly.year, monthly.month, monthly.highTemp, monthly.lowTemp,
      monthly.meanTemp, monthly.varianceTemp, monthly.stdevTemp)
}