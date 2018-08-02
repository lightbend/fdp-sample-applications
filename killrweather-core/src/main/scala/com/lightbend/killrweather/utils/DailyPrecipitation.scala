package com.lightbend.killrweather.utils

/**
 * Created by boris on 7/19/17.
 */
case class DailyPrecipitation(
  wsid: String,
  year: Int,
  month: Int,
  day: Int,
  precipitation: Double
) extends Serializable
object DailyPrecipitation {
  def apply(daily: DailyWeatherData): DailyPrecipitation =
    new DailyPrecipitation(daily.wsid, daily.year, daily.month, daily.day, daily.precip)
}

case class MonthlyPrecipitation(
  wsid: String,
  year: Int,
  month: Int,
  high: Double,
  low: Double,
  mean: Double,
  variance: Double,
  stdev: Double
) extends Serializable
object MonthlyPrecipitation {
  def apply(monthly: MonthlyWeatherData): MonthlyPrecipitation =
    new MonthlyPrecipitation(monthly.wsid, monthly.year, monthly.month, monthly.highPrecip, monthly.lowPrecip,
      monthly.meanPrecip, monthly.variancePrecip, monthly.stdevPrecip)
}