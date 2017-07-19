package com.lightbend.killrweather.utils

/**
  * Created by boris on 7/19/17.
  */
case class DailyWindSpeed(wsid: String,
                          year: Int,
                          month: Int,
                          day: Int,
                          high: Double,
                          low: Double,
                          mean: Double,
                          variance: Double,
                          stdev: Double) extends Serializable

object DailyWindSpeed{
  def apply(daily: DailyWeatherData): DailyWindSpeed =
    new DailyWindSpeed(daily.wsid, daily.year, daily.month, daily.day, daily.highWind, daily.lowWind,
      daily.meanWind, daily.varianceWind, daily.stdevWind)
}

case class MonthlyWindSpeed(wsid: String,
                          year: Int,
                          month: Int,
                          high: Double,
                          low: Double,
                          mean: Double,
                          variance: Double,
                          stdev: Double) extends Serializable

object MonthlyWindSpeed{
  def apply(monthly: MonthlyWeatherData): MonthlyWindSpeed =
    new MonthlyWindSpeed(monthly.wsid, monthly.year, monthly.month, monthly.highWind, monthly.lowWind,
      monthly.meanWind, monthly.varianceWind, monthly.stdevWind)
}