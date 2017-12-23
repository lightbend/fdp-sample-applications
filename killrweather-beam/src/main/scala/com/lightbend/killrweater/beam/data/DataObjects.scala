package com.lightbend.killrweater.beam.data

import com.lightbend.killrweather.WeatherClient.WeatherRecord
import scala.collection.JavaConverters._

case class RawWeatherData(year : Int, month : Int, day : Int, hour : Int, temperature : Double, dewpoint : Double,
                          pressure : Double, windDirection : Int, windSpeed : Double, skyCondition : Int,
                          skyConditionText : String, oneHourPrecip : Double, sixHourPrecip : Double) extends Serializable
object RawWeatherData{
  def apply(record : WeatherRecord): RawWeatherData = new RawWeatherData(record.year, record.month, record.day, record.hour,
    record.temperature, record.dewpoint, record.pressure, record.windDirection, record.windSpeed, record.skyCondition,
    record.skyConditionText, record.oneHourPrecip, record.sixHourPrecip)
}


case class DailyWeatherData(year: Int, month: Int, day: Int,
                            highTemp: Double, lowTemp: Double, meanTemp: Double, stdevTemp: Double, varianceTemp: Double,
                            highWind: Double, lowWind: Double, meanWind: Double, stdevWind: Double, varianceWind: Double,
                            highPressure: Double, lowPressure: Double, meanPressure: Double, stdevPressure: Double, variancePressure: Double,
                            precip: Double) extends Serializable

case class MonthlyWeatherData(year: Int, month: Int,
                              highTemp: Double, lowTemp: Double, meanTemp: Double, stdevTemp: Double, varianceTemp: Double,
                              highWind: Double, lowWind: Double, meanWind: Double, stdevWind: Double, varianceWind: Double,
                              highPressure: Double, lowPressure: Double, meanPressure: Double, stdevPressure: Double, variancePressure: Double,
                              highPrecip: Double, lowPrecip: Double, meanPrecip: Double, stdevPrecip: Double, variancePrecip: Double) extends Serializable

object DataObjects{

  def getRawTrigger(raw : RawWeatherData) : Int = raw.day
  def getDailyTrigger(daily : DailyWeatherData) : Int = daily.month

  def convertRawData(rawList: java.util.List[RawWeatherData]) : DailyWeatherData = {
    val raw = rawList.asScala
    val dailyPrecip = raw.foldLeft(.0)(_ + _.oneHourPrecip)
    val tempAggregate = StatCounter(raw.map(_.temperature))
    val windAggregate = StatCounter(raw.map(_.windSpeed))
    val pressureAggregate = StatCounter(raw.map(_.pressure).filter(_ > 1.0)) // remove 0 elements
    DailyWeatherData(raw.head.year, raw.head.month, raw.head.day,
      tempAggregate.max, tempAggregate.min, tempAggregate.mean, tempAggregate.stdev, tempAggregate.variance,
      windAggregate.max, windAggregate.min, windAggregate.mean, windAggregate.stdev, windAggregate.variance,
      pressureAggregate.max, pressureAggregate.min, pressureAggregate.mean, pressureAggregate.stdev, pressureAggregate.variance,
      dailyPrecip)
  }
    
    
  def convertDailyData(dailyList: java.util.List[DailyWeatherData]) : MonthlyWeatherData = {
    val daily = dailyList.asScala
    val tempAggregate = StatCounter(daily.map(_.meanTemp))
    val windAggregate = StatCounter(daily.map(_.meanWind))
    val pressureAggregate = StatCounter(daily.map(_.meanPressure))
    val presipAggregate = StatCounter(daily.map(_.precip))
    MonthlyWeatherData(daily.head.year, daily.head.month,
      tempAggregate.max, tempAggregate.min, tempAggregate.mean, tempAggregate.stdev, tempAggregate.variance,
      windAggregate.max, windAggregate.min, windAggregate.mean, windAggregate.stdev, windAggregate.variance,
      pressureAggregate.max, pressureAggregate.min, pressureAggregate.mean, pressureAggregate.stdev, pressureAggregate.variance,
      presipAggregate.max, presipAggregate.min, presipAggregate.mean, presipAggregate.stdev, presipAggregate.variance)

  }
}
