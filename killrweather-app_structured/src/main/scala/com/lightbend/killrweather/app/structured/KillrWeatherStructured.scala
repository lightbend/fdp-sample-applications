package com.lightbend.killrweather.app.structured

import com.lightbend.killrweather.WeatherClient.WeatherRecord
import com.lightbend.killrweather.app.structured.cassandra._
import com.lightbend.killrweather.app.structured.grafana.GrafanaSetup
import com.lightbend.killrweather.app.structured.influxDB._
import com.lightbend.killrweather.settings.WeatherSettings
import com.lightbend.killrweather.utils.{ DailyWeatherData, MonthlyWeatherData }
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object KillrWeatherStructured {

  def main(args: Array[String]): Unit = {

    // Create context

    WeatherSettings.handleArgs("KillrWeather", args)
    val settings = new WeatherSettings()
    import settings._

    val spark = SparkSession.builder
      .appName("KillrWeather with Structured Streaming")
      .master("local")
      .config("spark.cassandra.connection.host", "10.2.2.13" /*CassandraHosts*/ )
      .config("spark.sql.streaming.checkpointLocation", SparkCheckpointDir)
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .getOrCreate()

    // Initialize Cassandra
    try {
      new CassandraSetup(spark).setup()
    } catch {
      case t: Throwable => println(s"Cassandra not initialized ${t.getMessage}")
    }

    // Initialize Grafana
    try {
      new GrafanaSetup(4086, "10.2.2.198").setGrafana()
    } catch {
      case t: Throwable => println(s"Grafana not initialized ${t.getMessage}")
    }

    import spark.implicits._

    // Message parsing
    spark.udf.register("deserialize", (data: Array[Byte]) => WeatherRecord.parseFrom(data))

    // Read raw data
    val raw = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "10.2.2.221:1025" /*kafkaBrokers*/ )
      .option("subscribe", KafkaTopicRaw)
      .option("enable.auto.commit", false) // Cannot be set to true in Spark Strucutured Streaming https://spark.apache.org/docs/latest/structured-streaming-kafka-integration.html#kafka-specific-configurations
      .option("group.id", KafkaGroupId + ".structured")
      .option("startingOffsets", "earliest")
      .option("failOnDataLoss", "false")
      .load().selectExpr("""deserialize(value) AS message""").select("message")
      .select("message.wsid", "message.year", "message.month", "message.day", "message.hour", "message.temperature",
        "message.dewpoint", "message.pressure", "message.windDirection", "message.windSpeed", "message.skyCondition",
        "message.skyConditionText", "message.oneHourPrecip", "message.sixHourPrecip")

    /* Saves the raw data to Cassandra - raw table. */
    raw.select("wsid", "year", "month", "day", "hour", "temperature", "dewpoint", "pressure", "windDirection", "windSpeed",
      "skyCondition", "skyConditionText", "oneHourPrecip", "sixHourPrecip").as[WeatherRecord]
      .writeStream.queryName("saving raw").foreach(new CassandraSinkForEachKillrweatherRaw(spark)).start

    /* Saves the raw data to Influx - visualization */
    raw.select("wsid", "year", "month", "day", "hour", "temperature", "dewpoint", "pressure", "windDirection", "windSpeed",
      "skyCondition", "skyConditionText", "oneHourPrecip", "sixHourPrecip").as[WeatherRecord]
      .writeStream.queryName("displaying raw").foreach(new InfluxDBSinkForEachKillrweatherRaw()).start
    /*
    // testing
    val rawQuery = raw.writeStream
      .outputMode("update")
      .format("console")
      .start
*/
    // Calculate daily
    val daily = raw
      .groupBy("wsid", "year", "month", "day")
      .agg(
        max("temperature") as ("highTemp"), min("temperature") as ("lowTemp"), avg("temperature") as ("meanTemp"), variance("temperature") as ("varianceTemp"), stddev("temperature") as ("stdevTemp"),
        max("windSpeed") as ("highWind"), min("windSpeed") as ("lowWind"), avg("windSpeed") as ("meanWind"), variance("windSpeed") as ("varianceWind"), stddev("windSpeed") as ("stdevWind"),
        max("pressure") as ("highPressure"), min("pressure") as ("lowPressure"), avg("pressure") as ("meanPressure"), variance("pressure") as ("variancePressure"), stddev("pressure") as ("stdevPressure"),
        sum("oneHourPrecip").as("precip")
      )
    /*
    // testing
    val dailyQuery = daily.writeStream
      .outputMode("update")
      .format("console")
      .start
*/
    /* Saves the daily data to Cassandra - daily tables. */
    daily.select("*" /*"wsid", "year", "month", "day",
      "highTemp", "lowTemp", "meanTemp", "varianceTemp", "stdevTemp",
      "highWind", "lowWind", "meanWind", "varianceWind", "stdevWind",
      "highPressure", "lowPressure", "meanPressure", "variancePressure", "stdevPressure", "precip"*/ ).as[DailyWeatherData]
      .writeStream.queryName("saving daily").outputMode("update").foreach(new CassandraSinkForEachKillrweatherDaily(spark)).start

    /* Saves the raw data to Influx - visualization */
    daily.select("wsid", "year", "month", "day",
      "highTemp", "lowTemp", "meanTemp", "varianceTemp", "stdevTemp",
      "highWind", "lowWind", "meanWind", "varianceWind", "stdevWind",
      "highPressure", "lowPressure", "meanPressure", "variancePressure", "stdevPressure", "precip").as[DailyWeatherData]
      .writeStream.queryName("displaying daily").outputMode("update").foreach(new InfluxDBSinkForEachKillrweatherDaily()).start

    // Calculate monthly
    val monthly = raw
      .groupBy("wsid", "year", "month")
      .agg(
        max("temperature") as ("highTemp"), min("temperature") as ("lowTemp"), avg("temperature") as ("meanTemp"), variance("temperature") as ("varianceTemp"), stddev("temperature") as ("stdevTemp"),
        max("windSpeed") as ("highWind"), min("windSpeed") as ("lowWind"), avg("windSpeed") as ("meanWind"), variance("windSpeed") as ("varianceWind"), stddev("windSpeed") as ("stdevWind"),
        max("pressure") as ("highPressure"), min("pressure") as ("lowPressure"), avg("pressure") as ("meanPressure"), variance("pressure") as ("variancePressure"), stddev("pressure") as ("stdevPressure"),
        max("oneHourPrecip").as("highPrecip"), min("oneHourPrecip").as("lowPrecip"), avg("oneHourPrecip").as("meanPrecip"), variance("oneHourPrecip") as ("variancePrecip"), stddev("oneHourPrecip") as ("stdevPrecip")
      )

    /*
      val monthly = daily
      .groupBy("wsid", "year", "month")
      .agg(
        max("meanTemp") as ("mhighTemp"), min("meanTemp") as ("mlowTemp"), avg("meanTemp") as ("mmeanTemp"), variance("meanTemp") as ("mvarTemp"),
        max("meanWind") as ("mhighWind"), min("meanWind") as ("mlowWind"), avg("meanWind") as ("mmeanWind"), variance("meanWind") as ("mvarWind"),
        max("meanPressure") as ("mhighPressure"), min("meanPressure") as ("mlowPressure"), avg("meanPressure") as ("mmeanPressure"), variance("meanPressure") as ("mvarPressure"),
        max("precip").as("mhighPrecip"), min("precip").as("mlowPrecip"), avg("precip").as("mmeanPrecip"), variance("precip") as ("mvarPrecip")
      )
*/
    /* Saves the daily data to Cassandra - daily tables. */
    monthly.select("wsid", "year", "month",
      "highTemp", "lowTemp", "meanTemp", "varianceTemp", "stdevTemp",
      "highWind", "lowWind", "meanWind", "varianceWind", "stdevWind",
      "highPressure", "lowPressure", "meanPressure", "variancePressure", "stdevPressure",
      "highPrecip", "lowPrecip", "meanPrecip", "variancePrecip", "stdevPrecip").as[MonthlyWeatherData]
      .writeStream.queryName("saving monthly").outputMode("update").foreach(new CassandraSinkForEachKillrweatherMonthly(spark)).start

    /* Saves the raw data to Influx - visualization */
    monthly.select("wsid", "year", "month",
      "highTemp", "lowTemp", "meanTemp", "varianceTemp", "stdevTemp",
      "highWind", "lowWind", "meanWind", "varianceWind", "stdevWind",
      "highPressure", "lowPressure", "meanPressure", "variancePressure", "stdevPressure",
      "highPrecip", "lowPrecip", "meanPrecip", "variancePrecip", "stdevPrecip").as[MonthlyWeatherData]
      .writeStream.queryName("displaying monthly").outputMode("update").foreach(new InfluxDBSinkForEachKillrweatherMonthly()).start

    /*
    // testing
    val monthlyQuery = monthly.writeStream
      .outputMode("update")
      .format("console")
      .start
*/
    //Wait for all streams to finish
    spark.streams.awaitAnyTermination()
  }
}