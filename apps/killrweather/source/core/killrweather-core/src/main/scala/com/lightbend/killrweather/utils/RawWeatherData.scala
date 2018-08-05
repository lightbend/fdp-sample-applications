package com.lightbend.killrweather.utils

/**
 * @param wsid Composite of Air Force Datsav3 station number and NCDC WBAN number
 * @param year Year collected
 * @param month Month collected
 * @param day Day collected
 * @param hour Hour collected
 * @param temperature Air temperature (degrees Celsius)
 * @param dewpoint Dew point temperature (degrees Celsius)
 * @param pressure Sea level pressure (hectopascals)
 * @param windDirection Wind direction in degrees. 0-359
 * @param windSpeed Wind speed (meters per second)
 * @param skyCondition Total cloud cover (coded, see format documentation)
 * @param skyConditionText Non-coded sky conditions
 * @param oneHourPrecip One-hour accumulated liquid precipitation (millimeters)
 * @param sixHourPrecip Six-hour accumulated liquid precipitation (millimeters)
 */
case class RawWeatherData(
  wsid: String,
  year: Int,
  month: Int,
  day: Int,
  hour: Int,
  temperature: Double,
  dewpoint: Double,
  pressure: Double,
  windDirection: Int,
  windSpeed: Double,
  skyCondition: Int,
  skyConditionText: String,
  oneHourPrecip: Double,
  sixHourPrecip: Double
) extends Serializable

object RawWeatherData {
  /** Tech debt - don't do it this way ;) */
  def apply(array: Array[String]): RawWeatherData = {
    RawWeatherData(
      wsid = array(0),
      year = array(1).toInt,
      month = array(2).toInt,
      day = array(3).toInt,
      hour = array(4).toInt,
      temperature = array(5).toDouble,
      dewpoint = array(6).toDouble,
      pressure = array(7).toDouble,
      windDirection = array(8).toInt,
      windSpeed = array(9).toDouble,
      skyCondition = array(10).toInt,
      skyConditionText = "",
      oneHourPrecip = array(11).toDouble,
      sixHourPrecip = Option(array(12).toDouble).getOrElse(0)
    )
  }
}