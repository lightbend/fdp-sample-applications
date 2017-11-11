package com.lightbend.killrweather.app.structured.cassandra.streaming

import org.apache.spark.sql.{ DataFrame, SQLContext }
import com.datastax.spark.connector._
import com.lightbend.killrweather.WeatherClient.WeatherRecord
import com.lightbend.killrweather.settings.WeatherSettings
import org.apache.spark.sql.execution.streaming.Sink

/**
 * must be idempotent and synchronous (@TODO check asynchronous/synchronous from Datastax's Spark connector) sink
 */
class CassandraRawSink(sqlContext: SQLContext) extends Sink {
  private val spark = sqlContext.sparkSession
  import spark.implicits._

  val settings = new WeatherSettings()
  import settings._

  /*
   * As per SPARK-16020 arbitrary transformations are not supported, but
   * converting to an RDD allows us to do magic.
   */
  override def addBatch(batchId: Long, df: DataFrame) = {
    val ds = df.select("*").as[WeatherRecord]
    ds.rdd.saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableRaw)
  }
}

class CassandraDailySink(sqlContext: SQLContext) extends Sink {
  private val spark = sqlContext.sparkSession
  import spark.implicits._

  val settings = new WeatherSettings()
  import settings._

  /*
  * As per SPARK-16020 arbitrary transformations are not supported, but
  * converting to an RDD allows us to do magic.
  */
  override def addBatch(batchId: Long, df: DataFrame) = {
    val temp = df.select("wsid", "year", "month", "day",
      "highTemp", "lowTemp", "meanTemp", "varianceTemp", "stdevTemp").as[DailyTemperature]
    temp.rdd.saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableDailyTemp,
      SomeColumns("wsid", "year", "month", "day", "high" as "highTemp", "low" as "lowTemp", "mean" as "meanTemp",
        "variance" as "varianceTemp", "stdev" as "stdevTemp"))
    val pressure = df.select("wsid", "year", "month", "day",
      "highPressure", "lowPressure", "meanPressure", "variancePressure", "stdevPressure").as[DailyPressure]
    pressure.rdd.saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableDailyPressure,
      SomeColumns("wsid", "year", "month", "day", "high" as "highPressure", "low" as "lowPressure", "mean" as "meanPressure",
        "variance" as "variancePressure", "stdev" as "stdevPressure"))
    val wind = df.select("wsid", "year", "month", "day",
      "highWind", "lowWind", "meanWind", "varianceWind", "stdevWind").as[DailyWind]
    wind.rdd.saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableDailyWind,
      SomeColumns("wsid", "year", "month", "day", "high" as "highWind", "low" as "lowWind", "mean" as "meanWind",
        "variance" as "varianceWind", "stdev" as "stdevWind"))
    val precip = df.select("wsid", "year", "month", "day",
      "precip").as[DailyPrecipitation]
    precip.rdd.saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableDailyPrecip,
      SomeColumns("wsid", "year", "month", "day", "precipitation" as "precip"))
  }
}

class CassandraMonthlySink(sqlContext: SQLContext) extends Sink {
  private val spark = sqlContext.sparkSession
  import spark.implicits._

  val settings = new WeatherSettings()
  import settings._

  /*
  * As per SPARK-16020 arbitrary transformations are not supported, but
  * converting to an RDD allows us to do magic.
  */
  override def addBatch(batchId: Long, df: DataFrame) = {
    val temp = df.select("wsid", "year", "month",
      "highTemp", "lowTemp", "meanTemp", "varianceTemp", "stdevTemp").as[MonthlyTemperature]
    temp.rdd.saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableMonthlyTemp,
      SomeColumns("wsid", "year", "month", "high" as "highTemp", "low" as "lowTemp", "mean" as "meanTemp",
        "variance" as "varianceTemp", "stdev" as "stdevTemp"))
    val pressure = df.select("wsid", "year", "month",
      "highPressure", "lowPressure", "meanPressure", "variancePressure", "stdevPressure").as[MonthlyPressure]
    pressure.rdd.saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableMonthlyPressure,
      SomeColumns("wsid", "year", "month", "high" as "highPressure", "low" as "lowPressure", "mean" as "meanPressure",
        "variance" as "variancePressure", "stdev" as "stdevPressure"))
    val wind = df.select("wsid", "year", "month",
      "highWind", "lowWind", "meanWind", "varianceWind", "stdevWind").as[MonthlyWind]
    wind.rdd.saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableMonthlyWind,
      SomeColumns("wsid", "year", "month", "high" as "highWind", "low" as "lowWind", "mean" as "meanWind",
        "variance" as "varianceWind", "stdev" as "stdevWind"))
    val precip = df.select("wsid", "year", "month",
      "highPrecip", "lowPrecip", "meanPrecip", "variancePrecip", "stdevPrecip").as[MonthlyPrecipitation]
    precip.rdd.saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableMonthlyPrecip,
      SomeColumns("wsid", "year", "month", "high" as "highPrecip", "low" as "lowPrecip", "mean" as "meanPrecip",
        "variance" as "variancePrecip", "stdev" as "stdevPrecip"))
  }
}