package com.lightbend.killrweather.app.structured.influxDB.streaming

import java.util.concurrent.TimeUnit

import com.lightbend.killrweather.WeatherClient.WeatherRecord
import com.lightbend.killrweather.settings.WeatherSettings
import com.lightbend.killrweather.utils.{DailyWeatherData, MonthlyWeatherData}
import org.apache.spark.sql.{DataFrame, SQLContext}
import org.apache.spark.sql.execution.streaming.Sink
import org.influxdb.{InfluxDBFactory, InfluxDB}
import org.influxdb.dto.{Point, Query}
import collection.JavaConverters._

object InfluxDBSupport{
  val settings = WeatherSettings()
  import settings._

  def ensureInfluxDB(influxDB : InfluxDB) : Unit = {
    val databasesQuery = new Query("SHOW DATABASES","")
    val database_exists = influxDB.query(databasesQuery).getResults.get(0).getSeries.get(0).getValues match {
      case databases if databases == null => false
      case databases =>
        val names = databases.asScala.map(_.get(0).toString())
        if(names.contains(influxTableConfig.database)) true else false
    }
    if(!database_exists){
      val databaseCreateQuery = new Query(s"""CREATE DATABASE "${influxTableConfig.database}"""","")
      influxDB.query(databaseCreateQuery)
      val dropRetentionQuery = new Query("""DROP RETENTION POLICY "autogen"""",influxTableConfig.database)
      influxDB.query(dropRetentionQuery)
      val createRetentionQuery = new Query(s"""CREATE RETENTION POLICY "${influxTableConfig.retentionPolicy}" DURATION 1d SHARD DURATION 30m REPLICATION 1  DEFAULT""",influxTableConfig.database)
      influxDB.query(createRetentionQuery)
    }
  }

}

class InfluxDBRawSink(sqlContext: SQLContext) extends Sink with Serializable {
  private val spark = sqlContext.sparkSession
  import spark.implicits._

  val settings = WeatherSettings()
  import settings._

  override def addBatch(batchId: Long, df: DataFrame) = {

    val ds = df.select("*").as[WeatherRecord].rdd

    ds.foreachPartition { iter =>

      val influxDB = InfluxDBFactory.connect(influxConfig.url, influxConfig.user, influxConfig.password)
      InfluxDBSupport.ensureInfluxDB(influxDB)
      influxDB.setDatabase(influxTableConfig.database)
      // Flush every 2000 Points, at least every 100ms
      influxDB.enableBatch(2000, 100, TimeUnit.MILLISECONDS)
      // set retention policy
      influxDB.setRetentionPolicy(influxTableConfig.retentionPolicy)
      iter.foreach { raw =>
        {
          val rawPoint = Point.measurement("raw_weather").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .addField("year", raw.year.toLong).addField("month", raw.month.toLong).addField("day", raw.day.toLong)
            .addField("hour", raw.hour.toLong).addField("temperature", raw.temperature).addField("dewpoint", raw.dewpoint)
            .addField("pressure", raw.pressure).addField("windDirection", raw.windDirection.toLong).addField("windSpeed", raw.windSpeed)
            .addField("skyConditions", raw.skyCondition.toLong).tag("station", raw.wsid)
          influxDB.write(rawPoint.build())
        }
      }
      influxDB.flush()
      influxDB.close()
    }
  }
}

class InfluxDBDailySink(sqlContext: SQLContext) extends Sink with Serializable {
  private val spark = sqlContext.sparkSession
  import spark.implicits._

  val settings = WeatherSettings()
  import settings._

  override def addBatch(batchId: Long, df: DataFrame) = {

    val ds = df.select("*").as[DailyWeatherData].rdd

    ds.foreachPartition { iter =>
      val influxDB = InfluxDBFactory.connect(influxConfig.url, influxConfig.user, influxConfig.password)
      InfluxDBSupport.ensureInfluxDB(influxDB)

      influxDB.setDatabase(influxTableConfig.database)
      // Flush every 2000 Points, at least every 100ms
      influxDB.enableBatch(2000, 100, TimeUnit.MILLISECONDS)
      // set retention policy
      influxDB.setRetentionPolicy(influxTableConfig.retentionPolicy)
      iter.foreach { dailyTemp =>
        {
          val dailyTempPoint = Point.measurement("daily_temp_weather").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .addField("year", dailyTemp.year.toLong).addField("month", dailyTemp.month.toLong).addField("day", dailyTemp.day.toLong)
            .addField("high", dailyTemp.highTemp).addField("low", dailyTemp.lowTemp).addField("mean", dailyTemp.meanTemp)
            .addField("variance", dailyTemp.varianceTemp).addField("stdev", dailyTemp.stdevTemp).tag("station", dailyTemp.wsid)
          influxDB.write(dailyTempPoint.build())
        }
      }
      influxDB.flush()
      influxDB.close()
    }
  }
}

class InfluxDBRMonthlySink(sqlContext: SQLContext) extends Sink with Serializable {
  private val spark = sqlContext.sparkSession
  import spark.implicits._

  val settings = WeatherSettings()
  import settings._

  override def addBatch(batchId: Long, df: DataFrame) = {

    val ds = df.select("*").as[MonthlyWeatherData].rdd

    ds.foreachPartition { iter =>

      val influxDB = InfluxDBFactory.connect(influxConfig.url, influxConfig.user, influxConfig.password)
      InfluxDBSupport.ensureInfluxDB(influxDB)

      influxDB.setDatabase(influxTableConfig.database)
      // Flush every 2000 Points, at least every 100ms
      influxDB.enableBatch(2000, 100, TimeUnit.MILLISECONDS)
      // set retention policy
      influxDB.setRetentionPolicy(influxTableConfig.retentionPolicy)
      iter.foreach { monthlyTemp =>
        {
          val monthlyTempPoint = Point.measurement("monthly_temp_weather").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .addField("year", monthlyTemp.year.toLong).addField("month", monthlyTemp.month.toLong).addField("high", monthlyTemp.highTemp)
            .addField("low", monthlyTemp.lowTemp).addField("mean", monthlyTemp.meanTemp).addField("variance", monthlyTemp.varianceTemp)
            .addField("stdev", monthlyTemp.stdevTemp).tag("station", monthlyTemp.wsid)
          influxDB.write(monthlyTempPoint.build())
        }
      }
      influxDB.flush()
      influxDB.close()
    }
  }
}