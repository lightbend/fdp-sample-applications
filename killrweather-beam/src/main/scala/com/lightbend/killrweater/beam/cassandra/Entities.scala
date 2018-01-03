package com.lightbend.killrweater.beam.cassandra

import com.datastax.driver.mapping.annotations.{Column, Table}
import java.io.Serializable

import com.lightbend.killrweater.beam.data.{DailyWeatherData, MonthlyWeatherData, RawWeatherData}
import com.lightbend.killrweather.utils.MonthlyWindSpeed
import org.apache.beam.sdk.values.KV


@Table(name = "raw_weather_data", keyspace = "isd_weather_data") class RawEntity extends Serializable {
  @Column(name = "wsid") var wsid : String = null
  @Column(name = "year") var year = 0
  @Column(name = "month") var month = 0
  @Column(name = "day") var day = 0
  @Column(name = "hour") var hour = 0
  @Column(name = "temperature") var temperature = .0
  @Column(name = "dewpoint") var dewpoint = .0
  @Column(name = "pressure") var pressure = .0
  @Column(name = "wind_direction") var windDirection = 0
  @Column(name = "wind_speed") var windSpeed = .0
  @Column(name = "sky_condition") var skyCondition = 0
  @Column(name = "sky_condition_text") var skyConditionText: String = null
  @Column(name = "one_hour_precip") var oneHourPrecip = .0
  @Column(name = "six_hour_precip") var sixHourPrecip = .0
}

@Table(name = "daily_aggregate_temperature", keyspace = "isd_weather_data") class DailyTemperatureEntity extends Serializable {
  @Column(name = "wsid") var wsid: String = null
  @Column(name = "year") var year = 0
  @Column(name = "month") var month = 0
  @Column(name = "day") var day = 0
  @Column(name = "high") var high = .0
  @Column(name = "low") var low = .0
  @Column(name = "mean") var mean = .0
  @Column(name = "variance") var variance = .0
  @Column(name = "stdev") var stdev = .0
}

@Table(name = "daily_aggregate_windspeed", keyspace = "isd_weather_data") class DailyWindEntity extends Serializable {
  @Column(name = "wsid") var wsid: String = null
  @Column(name = "year") var year = 0
  @Column(name = "month") var month = 0
  @Column(name = "day") var day = 0
  @Column(name = "high") var high = .0
  @Column(name = "low") var low = .0
  @Column(name = "mean") var mean = .0
  @Column(name = "variance") var variance = .0
  @Column(name = "stdev") var stdev = .0
}

@Table(name = "daily_aggregate_pressure", keyspace = "isd_weather_data") class DailyPressureEntity extends Serializable {
  @Column(name = "wsid") var wsid: String = null
  @Column(name = "year") var year = 0
  @Column(name = "month") var month = 0
  @Column(name = "day") var day = 0
  @Column(name = "high") var high = .0
  @Column(name = "low") var low = .0
  @Column(name = "mean") var mean = .0
  @Column(name = "variance") var variance = .0
  @Column(name = "stdev") var stdev = .0
}

@Table(name = "daily_aggregate_precip", keyspace = "isd_weather_data") class DailyPrecipEntity extends Serializable {
  @Column(name = "wsid") var wsid: String = null
  @Column(name = "year") var year = 0
  @Column(name = "month") var month = 0
  @Column(name = "day") var day = 0
  @Column(name = "precipitation") var precipitation = .0
}

@Table(name = "monthly_aggregate_temperature", keyspace = "isd_weather_data") class MonthlyTemperatureEntity extends Serializable {
  @Column(name = "wsid") var wsid: String = null
  @Column(name = "year") var year = 0
  @Column(name = "month") var month = 0
  @Column(name = "high") var high = .0
  @Column(name = "low") var low = .0
  @Column(name = "mean") var mean = .0
  @Column(name = "variance") var variance = .0
  @Column(name = "stdev") var stdev = .0
}

@Table(name = "monthly_aggregate_windspeed", keyspace = "isd_weather_data") class MonthlyWindEntity extends Serializable {
  @Column(name = "wsid") var wsid: String = null
  @Column(name = "year") var year = 0
  @Column(name = "month") var month = 0
  @Column(name = "high") var high = .0
  @Column(name = "low") var low = .0
  @Column(name = "mean") var mean = .0
  @Column(name = "variance") var variance = .0
  @Column(name = "stdev") var stdev = .0
}

@Table(name = "monthly_aggregate_pressure", keyspace = "isd_weather_data") class MonthlyPressureEntity extends Serializable {
  @Column(name = "wsid") var wsid: String = null
  @Column(name = "year") var year = 0
  @Column(name = "month") var month = 0
  @Column(name = "high") var high = .0
  @Column(name = "low") var low = .0
  @Column(name = "mean") var mean = .0
  @Column(name = "variance") var variance = .0
  @Column(name = "stdev") var stdev = .0
}

@Table(name = "monthly_aggregate_precip", keyspace = "isd_weather_data") class MonthlyPrecipEntity extends Serializable {
  @Column(name = "wsid") var wsid: String = null
  @Column(name = "year") var year = 0
  @Column(name = "month") var month = 0
  @Column(name = "high") var high = .0
  @Column(name = "low") var low = .0
  @Column(name = "mean") var mean = .0
  @Column(name = "variance") var variance = .0
  @Column(name = "stdev") var stdev = .0
}


object Entities{
  def getRawEntity(record : KV[String, RawWeatherData]) : RawEntity = {
    val entity = new RawEntity()
    entity.wsid = record.getKey
    entity.year = record.getValue.year
    entity.month = record.getValue.month
    entity.day = record.getValue.day
    entity.hour = record.getValue.hour
    entity.temperature = record.getValue.temperature
    entity.dewpoint = record.getValue.dewpoint
    entity.pressure = record.getValue.pressure
    entity.windDirection = record.getValue.windDirection
    entity.windSpeed = record.getValue.windSpeed
    entity.skyCondition = record.getValue.skyCondition
    entity.skyConditionText = record.getValue.skyConditionText
    entity.oneHourPrecip = record.getValue.oneHourPrecip
    entity.sixHourPrecip = record.getValue.sixHourPrecip
    entity
  }

  def getDailyTempEntity(record : KV[String, DailyWeatherData]) : DailyTemperatureEntity = {
    val entity = new DailyTemperatureEntity()
    entity.wsid = record.getKey
    entity.year = record.getValue.year
    entity.month = record.getValue.month
    entity.day = record.getValue.day
    entity.high = record.getValue.highTemp
    entity.low = record.getValue.lowTemp
    entity.mean = record.getValue.meanTemp
    entity.stdev = record.getValue.stdevTemp
    entity.variance = record.getValue.varianceTemp
    entity
  }

  def getDailyWindEntity(record : KV[String, DailyWeatherData]) : DailyWindEntity = {
    val entity = new DailyWindEntity()
    entity.wsid = record.getKey
    entity.year = record.getValue.year
    entity.month = record.getValue.month
    entity.day = record.getValue.day
    entity.high = record.getValue.highWind
    entity.low = record.getValue.lowWind
    entity.mean = record.getValue.meanWind
    entity.stdev = record.getValue.stdevWind
    entity.variance = record.getValue.varianceWind
    entity
  }

  def getDailyPressureEntity(record : KV[String, DailyWeatherData]) : DailyPressureEntity = {
    val entity = new DailyPressureEntity()
    entity.wsid = record.getKey
    entity.year = record.getValue.year
    entity.month = record.getValue.month
    entity.day = record.getValue.day
    entity.high = record.getValue.highPressure
    entity.low = record.getValue.lowPressure
    entity.mean = record.getValue.meanPressure
    entity.stdev = record.getValue.stdevPressure
    entity.variance = record.getValue.variancePressure
    entity
  }

  def getDailyPrecipEntity(record : KV[String, DailyWeatherData]) : DailyPrecipEntity = {
    val entity = new DailyPrecipEntity
    entity.wsid = record.getKey
    entity.year = record.getValue.year
    entity.month = record.getValue.month
    entity.day = record.getValue.day
    entity.precipitation = record.getValue.precip
    entity
  }

  def getMonthlyTempEntity(record : KV[String, MonthlyWeatherData]) : MonthlyTemperatureEntity = {
    val entity = new MonthlyTemperatureEntity()
    entity.wsid = record.getKey
    entity.year = record.getValue.year
    entity.month = record.getValue.month
    entity.high = record.getValue.highTemp
    entity.low = record.getValue.lowTemp
    entity.mean = record.getValue.meanTemp
    entity.stdev = record.getValue.stdevTemp
    entity.variance = record.getValue.varianceTemp
    entity
  }

  def getMonthlyWindEntity(record : KV[String, MonthlyWeatherData]) : MonthlyWindEntity = {
    val entity = new MonthlyWindEntity()
    entity.wsid = record.getKey
    entity.year = record.getValue.year
    entity.month = record.getValue.month
    entity.high = record.getValue.highTemp
    entity.low = record.getValue.lowTemp
    entity.mean = record.getValue.meanTemp
    entity.stdev = record.getValue.stdevTemp
    entity.variance = record.getValue.varianceTemp
    entity
  }

  def getMonthlyPressureEntity(record : KV[String, MonthlyWeatherData]) : MonthlyPressureEntity = {
    val entity = new MonthlyPressureEntity
    entity.wsid = record.getKey
    entity.year = record.getValue.year
    entity.month = record.getValue.month
    entity.high = record.getValue.highTemp
    entity.low = record.getValue.lowTemp
    entity.mean = record.getValue.meanTemp
    entity.stdev = record.getValue.stdevTemp
    entity.variance = record.getValue.varianceTemp
    entity
  }

  def getMonthlyPrecipEntity(record : KV[String, MonthlyWeatherData]) : MonthlyPrecipEntity = {
    val entity = new MonthlyPrecipEntity
    entity.wsid = record.getKey
    entity.year = record.getValue.year
    entity.month = record.getValue.month
    entity.high = record.getValue.highTemp
    entity.low = record.getValue.lowTemp
    entity.mean = record.getValue.meanTemp
    entity.stdev = record.getValue.stdevTemp
    entity.variance = record.getValue.varianceTemp
    entity
  }
}