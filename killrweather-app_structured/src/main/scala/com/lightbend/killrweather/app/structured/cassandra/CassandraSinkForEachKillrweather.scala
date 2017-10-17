package com.lightbend.killrweather.app.structured.cassandra

import com.datastax.spark.connector.cql.CassandraConnector
import com.lightbend.killrweather.WeatherClient.WeatherRecord
import com.lightbend.killrweather.settings.WeatherSettings
import com.lightbend.killrweather.utils.{ DailyWeatherData, MonthlyWeatherData }
import org.apache.spark.sql.ForeachWriter
import org.apache.spark.sql.SparkSession

/* This implementation is based on several sources:
 * https://github.com/polomarcus/Spark-Structured-Streaming-Examples/blob/master/src/main/scala/cassandra/foreachSink/CassandraSinkForeach.scala
 * https://github.com/ansrivas/spark-structured-streaming/blob/master/src/main/scala/com/kafkaToSparkToCass/Main.scala
 * https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html#using-foreach
 * The general problem is that this is not generic. ForeachWriter is parametrized with a specific type, so we need per type implementations
*/

class CassandraSinkForEachKillrweatherRaw(sparkSession: SparkSession) extends ForeachWriter[WeatherRecord] {

  val settings = new WeatherSettings()
  import settings._

  private def cqlRaw(record: WeatherRecord): String = s"""
       insert into $CassandraKeyspace.$CassandraTableRaw (wsid, year, month, day, hour, temperature, dewpoint, pressure,
       wind_direction, wind_speed, sky_condition, sky_condition_text, one_hour_precip, six_hour_precip)
       values('${record.wsid}', ${record.year}, ${record.month}, ${record.day}, ${record.hour}, ${record.temperature},
       ${record.dewpoint}, ${record.pressure}, ${record.windDirection}, ${record.windSpeed}, ${record.skyCondition},
       '${record.skyConditionText}', ${record.oneHourPrecip}, ${record.sixHourPrecip} )"""

  val connector = CassandraConnector.apply(sparkSession.sparkContext.getConf)

  override def open(partitionId: Long, version: Long): Boolean = {
    // open connection
    //@TODO command to check if cassandra cluster is up
    true
  }

  override def process(record: WeatherRecord): Unit = {
    val _ = connector.withSessionDo { session =>
      session.execute(cqlRaw(record))
    }
  }

  override def close(errorOrNull: Throwable): Unit = {} // close the connection
}

class CassandraSinkForEachKillrweatherDaily(sparkSession: SparkSession) extends ForeachWriter[DailyWeatherData] {

  val settings = new WeatherSettings()
  import settings._

  private def cqlTemp(record: DailyWeatherData): String = s"""
       insert into $CassandraKeyspace.$CassandraTableDailyTemp (wsid, year, month, day, high, low, mean, variance, stdev)
       values('${record.wsid}', ${record.year}, ${record.month}, ${record.day},
          ${record.highTemp},  ${record.lowTemp},  ${record.meanTemp}, ${record.varianceTemp}, ${record.stdevTemp})"""
  private def cqlPressure(record: DailyWeatherData): String = s"""
       insert into $CassandraKeyspace.$CassandraTableDailyPressure (wsid, year, month, day, high, low, mean, variance, stdev)
       values('${record.wsid}', ${record.year}, ${record.month}, ${record.day},
          ${record.highPressure},  ${record.lowPressure},  ${record.meanPressure}, ${record.variancePressure}, ${record.stdevPressure})"""
  private def cqlWind(record: DailyWeatherData): String = s"""
       insert into $CassandraKeyspace.$CassandraTableDailyWind (wsid, year, month, day, high, low, mean, variance, stdev)
       values('${record.wsid}', ${record.year}, ${record.month}, ${record.day},
          ${record.highWind},  ${record.lowWind},  ${record.meanWind}, ${record.varianceWind}, ${record.stdevWind})"""
  private def cqlPrecip(record: DailyWeatherData): String = s"""
       insert into $CassandraKeyspace.$CassandraTableDailyPrecip (wsid, year, month, day, precipitation)
       values('${record.wsid}', ${record.year}, ${record.month}, ${record.day}, ${record.precip})"""

  val connector = CassandraConnector.apply(sparkSession.sparkContext.getConf)

  override def open(partitionId: Long, version: Long): Boolean = {
    // open connection
    //@TODO command to check if cassandra cluster is up
    true
  }

  override def process(record: DailyWeatherData): Unit = {
    val _ = connector.withSessionDo { session =>
      session.execute(cqlTemp(record))
      session.execute(cqlPressure(record))
      session.execute(cqlWind(record))
      session.execute(cqlPrecip(record))
    }
  }

  override def close(errorOrNull: Throwable): Unit = {} // close the connection
}

class CassandraSinkForEachKillrweatherMonthly(sparkSession: SparkSession) extends ForeachWriter[MonthlyWeatherData] {

  val settings = new WeatherSettings()
  import settings._

  private def cqlTemp(record: MonthlyWeatherData): String = s"""
       insert into $CassandraKeyspace.$CassandraTableMonthlyTemp (wsid, year, month, high, low, mean, variance, stdev)
       values('${record.wsid}', ${record.year}, ${record.month},
          ${record.highTemp},  ${record.lowTemp},  ${record.meanTemp}, ${record.varianceTemp}, ${record.stdevTemp})"""
  private def cqlPressure(record: MonthlyWeatherData): String = s"""
       insert into $CassandraKeyspace.$CassandraTableMonthlyPressure (wsid, year, month, high, low, mean, variance, stdev)
       values('${record.wsid}', ${record.year}, ${record.month},
          ${record.highPressure},  ${record.lowPressure},  ${record.meanPressure}, ${record.variancePressure}, ${record.stdevPressure})"""
  private def cqlWind(record: MonthlyWeatherData): String = s"""
       insert into $CassandraKeyspace.$CassandraTableMonthlyWind (wsid, year, month, high, low, mean, variance, stdev)
       values('${record.wsid}', ${record.year}, ${record.month},
          ${record.highWind},  ${record.lowWind},  ${record.meanWind}, ${record.varianceWind}, ${record.stdevWind})"""
  private def cqlPrecip(record: MonthlyWeatherData): String = s"""
       insert into $CassandraKeyspace.$CassandraTableMonthlyPrecip (wsid, year, month,  high, low, mean, variance, stdev)
       values('${record.wsid}', ${record.year}, ${record.month},
          ${record.highPrecip},  ${record.lowPrecip},  ${record.meanPrecip}, ${record.variancePrecip}, ${record.stdevPrecip})"""

  val connector = CassandraConnector.apply(sparkSession.sparkContext.getConf)

  override def open(partitionId: Long, version: Long): Boolean = {
    // open connection
    //@TODO command to check if cassandra cluster is up
    true
  }

  override def process(record: MonthlyWeatherData): Unit = {
    val _ = connector.withSessionDo { session =>
      session.execute(cqlTemp(record))
      session.execute(cqlPressure(record))
      session.execute(cqlWind(record))
      session.execute(cqlPrecip(record))
    }
  }

  override def close(errorOrNull: Throwable): Unit = {} // close the connection
}
