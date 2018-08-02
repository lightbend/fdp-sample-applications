package com.lightbend.killrweather.utils

/**
 * Created by boris on 7/19/17.
 */
case class DailyPressure(
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

object DailyPressure {
  def apply(daily: DailyWeatherData): DailyPressure =
    new DailyPressure(daily.wsid, daily.year, daily.month, daily.day, daily.highPressure, daily.lowPressure,
      daily.meanPressure, daily.variancePressure, daily.stdevPressure)
}

case class MonthlyPressure(
  wsid: String,
  year: Int,
  month: Int,
  high: Double,
  low: Double,
  mean: Double,
  variance: Double,
  stdev: Double
) extends Serializable

object MonthlyPressure {
  def apply(monthly: MonthlyWeatherData): MonthlyPressure =
    new MonthlyPressure(monthly.wsid, monthly.year, monthly.month, monthly.highPressure, monthly.lowPressure,
      monthly.meanPressure, monthly.variancePressure, monthly.stdevPressure)
}