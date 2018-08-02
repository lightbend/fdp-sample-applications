package com.lightbend.killrweather.app.structured.cassandra.streaming

case class DailyTemperature(wsid: String, year: Int, month: Int, day: Int,
  highTemp: Double, lowTemp: Double, meanTemp: Double, varianceTemp: Double, stdevTemp: Double) extends Serializable

case class DailyPressure(wsid: String, year: Int, month: Int, day: Int,
  highPressure: Double, lowPressure: Double, meanPressure: Double, variancePressure: Double, stdevPressure: Double) extends Serializable

case class DailyWind(wsid: String, year: Int, month: Int, day: Int,
  highWind: Double, lowWind: Double, meanWind: Double, varianceWind: Double, stdevWind: Double) extends Serializable

case class DailyPrecipitation(wsid: String, year: Int, month: Int, day: Int, precip: Double) extends Serializable

case class MonthlyTemperature(wsid: String, year: Int, month: Int,
  highTemp: Double, lowTemp: Double, meanTemp: Double, varianceTemp: Double, stdevTemp: Double) extends Serializable

case class MonthlyPressure(wsid: String, year: Int, month: Int,
  highPressure: Double, lowPressure: Double, meanPressure: Double, variancePressure: Double, stdevPressure: Double) extends Serializable

case class MonthlyWind(wsid: String, year: Int, month: Int,
  highWind: Double, lowWind: Double, meanWind: Double, varianceWind: Double, stdevWind: Double) extends Serializable

case class MonthlyPrecipitation(wsid: String, year: Int, month: Int,
  highPrecip: Double, lowPrecip: Double, meanPrecip: Double, variancePrecip: Double, stdevPrecip: Double) extends Serializable
