package com.lightbend.killrweather.app.influxdb

import java.util.concurrent.TimeUnit

import com.lightbend.killrweather.WeatherClient.WeatherRecord
import com.lightbend.killrweather.settings.WeatherSettings
import com.lightbend.killrweather.utils.{DailyTemperature, MonthlyTemperature}
import org.influxdb.dto.Point
import org.influxdb.{InfluxDB, InfluxDBFactory}

class InfluxDBSink (createWriter: () => InfluxDB) extends Serializable{

  lazy val influxDB = createWriter()

  def write(raw : WeatherRecord, timestamp : Long ) : Unit = {
    val rawPoint = Point.measurement("raw_weather").time(timestamp, TimeUnit.MILLISECONDS)
    rawPoint.addField("year",         raw.year.toLong)
    rawPoint.addField("month",        raw.month.toLong)
    rawPoint.addField("day",          raw.day.toLong)
    rawPoint.addField("hour",         raw.hour.toLong)
    rawPoint.addField("temperature",  raw.temperature)
    rawPoint.addField("dewpoint",     raw.dewpoint)
    rawPoint.addField("pressure",     raw.pressure)
    rawPoint.addField("windDirection",raw.windDirection.toLong)
    rawPoint.addField("windSpeed",    raw.windSpeed)
    rawPoint.addField("skyConditions",raw.skyCondition.toLong)
    rawPoint.tag(     "station",      raw.wsid)
    influxDB.write(rawPoint.build())
  }

  def write(dailyTemp : DailyTemperature, timestamp : Long) : Unit = {
    val dailyTempPoint = Point.measurement("daily_temp_weather").time(timestamp, TimeUnit.MILLISECONDS)
    dailyTempPoint.addField("year",     dailyTemp.year.toLong)
    dailyTempPoint.addField("month",    dailyTemp.month.toLong)
    dailyTempPoint.addField("day",      dailyTemp.day.toLong)
    dailyTempPoint.addField("high",     dailyTemp.high)
    dailyTempPoint.addField("low",      dailyTemp.low)
    dailyTempPoint.addField("mean",     dailyTemp.mean)
    dailyTempPoint.addField("variance", dailyTemp.variance)
    dailyTempPoint.addField("stdev",    dailyTemp.stdev)
    dailyTempPoint.tag(     "station",  dailyTemp.wsid)
    influxDB.write(dailyTempPoint.build())
  }

  def write(monthlyTemp : MonthlyTemperature, timestamp : Long) : Unit = {
    val monthlyTempPoint = Point.measurement("daily_temp_weather").time(timestamp, TimeUnit.MILLISECONDS)
    monthlyTempPoint.addField("year",     monthlyTemp.year.toLong)
    monthlyTempPoint.addField("month",    monthlyTemp.month.toLong)
    monthlyTempPoint.addField("high",     monthlyTemp.high)
    monthlyTempPoint.addField("low",      monthlyTemp.low)
    monthlyTempPoint.addField("mean",     monthlyTemp.mean)
    monthlyTempPoint.addField("variance", monthlyTemp.variance)
    monthlyTempPoint.addField("stdev",    monthlyTemp.stdev)
    monthlyTempPoint.tag(     "station",  monthlyTemp.wsid)
    influxDB.write(monthlyTempPoint.build())
  }
}

object InfluxDBSink {

  val settings = new WeatherSettings()
  import settings._


  def apply(): InfluxDBSink = {
    val f = () => {
      val influxDB = InfluxDBFactory.connect(s"$influxDBServer:$influxDBPort", influxDBUser, influxDBPass)
      if (!influxDB.databaseExists(influxDBDatabase))
        influxDB.createDatabase(influxDBDatabase)

      influxDB.setDatabase(influxDBDatabase)
      // Flush every 2000 Points, at least every 100ms
      influxDB.enableBatch(2000, 100, TimeUnit.MILLISECONDS)
      // set retention policy
      influxDB.setRetentionPolicy(retentionPolicy)

      sys.addShutdownHook {
        influxDB.flush()
        influxDB.close()
      }
      influxDB
    }
    new InfluxDBSink(f)
  }
}