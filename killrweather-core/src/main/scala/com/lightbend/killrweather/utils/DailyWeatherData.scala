package com.lightbend.killrweather.utils

/**
 * Created by boris on 7/19/17.
 */
case class DailyWeatherData(wsid: String, year: Int, month: Int, day: Int,
  highTemp: Double, lowTemp: Double, meanTemp: Double, stdevTemp: Double, varianceTemp: Double,
  highWind: Double, lowWind: Double, meanWind: Double, stdevWind: Double, varianceWind: Double,
  highPressure: Double, lowPressure: Double, meanPressure: Double, stdevPressure: Double, variancePressure: Double,
  precip: Double) extends Serializable

case class DailyWeatherDataProcess(wsid: String, year: Int, month: Int, temp: Double, wind: Double,
  pressure: Double, precip: Double) extends Serializable

object DailyWeatherDataProcess {
  def apply(daily: DailyWeatherData): DailyWeatherDataProcess =
    new DailyWeatherDataProcess(daily.wsid, daily.year, daily.month, daily.meanTemp, daily.meanWind, daily.meanPressure, daily.precip)
}

case class MonthlyWeatherData(wsid: String, year: Int, month: Int,
  highTemp: Double, lowTemp: Double, meanTemp: Double, stdevTemp: Double, varianceTemp: Double,
  highWind: Double, lowWind: Double, meanWind: Double, stdevWind: Double, varianceWind: Double,
  highPressure: Double, lowPressure: Double, meanPressure: Double, stdevPressure: Double, variancePressure: Double,
  highPrecip: Double, lowPrecip: Double, meanPrecip: Double, stdevPrecip: Double, variancePrecip: Double) extends Serializable
