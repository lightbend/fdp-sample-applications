package com.lightbend.killrweather.app.structured

import com.lightbend.killrweather.WeatherClient.WeatherRecord
import com.lightbend.killrweather.app.structured.cassandra._
import com.lightbend.killrweather.app.structured.grafana.GrafanaSetup
//import com.lightbend.killrweather.app.structured.influxDB._
import com.lightbend.killrweather.settings.WeatherSettings
//import com.lightbend.killrweather.utils.{ DailyWeatherData, MonthlyWeatherData }
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object KillrWeatherStructured {

  def main(args: Array[String]): Unit = {

    // Create context
    val settings = WeatherSettings("KillrWeather", args)
    import settings._

    println(s"Running KillrweatherStructured. Kafka: $kafkaBrokers; Cassandra : $CassandraHosts; " +
      s"InfluxDB : host $influxDBServer, port $influxDBPort; Grafana : host $GrafanaServer, port $GrafanaPort")
    val spark = SparkSession.builder
      .appName("KillrWeather with Structured Streaming")
      .config(settings.sparkConf())
      .config("spark.sql.streaming.checkpointLocation", streamingConfig.checkpointDir)
      .getOrCreate()

    // Initialize Cassandra
    try {
      new CassandraSetup(spark).setup()
    } catch {
      case t: Throwable => println(s"Cassandra not initialized ${t.getMessage}")
    }

    // Initialize Grafana
    try
      //new GrafanaSetup(4086, "10.2.2.198").setGrafana()
      new GrafanaSetup().setGrafana()
    catch {
      case t: Throwable => println(s"Grafana not initialized ${t.getMessage}")
    }

    // Message parsing
    spark.udf.register("deserialize", (data: Array[Byte]) => WeatherRecord.parseFrom(data))

    // Read raw data
    val raw = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaConfig.brokers)
      .option("subscribe", kafkaConfig.topic)
      //      .option(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true) // Cannot be set to true in Spark Strucutured Streaming https://spark.apache.org/docs/latest/structured-streaming-kafka-integration.html#kafka-specific-configurations
      .option(ConsumerConfig.GROUP_ID_CONFIG, kafkaConfig.group)
      .option(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
      .option("startingOffsets", "latest")
      .option("failOnDataLoss", "false")
      .load().selectExpr("""deserialize(value) AS message""").select("message")
      .select("message.wsid", "message.year", "message.month", "message.day", "message.hour", "message.temperature",
        "message.dewpoint", "message.pressure", "message.windDirection", "message.windSpeed", "message.skyCondition",
        "message.skyConditionText", "message.oneHourPrecip", "message.sixHourPrecip")
    /*
    // testing
    val rawQuery = raw.writeStream
      .outputMode("update")
      .format("console")
      .start
*/
    /* Saves the raw data to Cassandra - raw table. */

    raw.writeStream
      .format("com.lightbend.killrweather.app.structured.cassandra.streaming.CassandraRawSinkProvider")
      .outputMode("update")
      .queryName("DataToCassandraRawStreamSinkProvider")
      .start()

    raw.writeStream
      .format("com.lightbend.killrweather.app.structured.influxDB.streaming.InfluxDBRawSinkProvider")
      .outputMode("update")
      .queryName("DataToInfluxDBBRawStreamSinkProvider")
      .start()
    /*
    raw.select("wsid", "year", "month", "day", "hour", "temperature", "dewpoint", "pressure", "windDirection", "windSpeed",
      "skyCondition", "skyConditionText", "oneHourPrecip", "sixHourPrecip").as[WeatherRecord]
      .writeStream.queryName("saving raw").foreach(new CassandraSinkForEachKillrweatherRaw(spark)).start

    /* Saves the raw data to Influx - visualization */
    raw.select("wsid", "year", "month", "day", "hour", "temperature", "dewpoint", "pressure", "windDirection", "windSpeed",
      "skyCondition", "skyConditionText", "oneHourPrecip", "sixHourPrecip").as[WeatherRecord]
      .writeStream.queryName("displaying raw").foreach(new InfluxDBSinkForEachKillrweatherRaw()).start
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

    // testing
    val dailyQuery = daily.writeStream
      .outputMode("update")
      .format("console")
      .start

    /* Saves the daily data to Cassandra - daily tables. */
    daily.writeStream
      .format("com.lightbend.killrweather.app.structured.cassandra.streaming.CassandraDailySinkProvider")
      .outputMode("update")
      .queryName("DataToCassandraDailyStreamSinkProvider")
      .start()

    daily.writeStream
      .format("com.lightbend.killrweather.app.structured.influxDB.streaming.InfluxDBDailySinkProvider")
      .outputMode("update")
      .queryName("DataToInfluxDBDailyStreamSinkProvider")
      .start()

    /*
    daily.select("wsid", "year", "month", "day",
      "highTemp", "lowTemp", "meanTemp", "varianceTemp", "stdevTemp",
      "highWind", "lowWind", "meanWind", "varianceWind", "stdevWind",
      "highPressure", "lowPressure", "meanPressure", "variancePressure", "stdevPressure", "precip").as[DailyWeatherData]
      .writeStream.queryName("saving daily").outputMode("update").foreach(new CassandraSinkForEachKillrweatherDaily(spark)).start

    /* Saves the daily data to Influx - visualization */
    daily.select("wsid", "year", "month", "day",
      "highTemp", "lowTemp", "meanTemp", "varianceTemp", "stdevTemp",
      "highWind", "lowWind", "meanWind", "varianceWind", "stdevWind",
      "highPressure", "lowPressure", "meanPressure", "variancePressure", "stdevPressure", "precip").as[DailyWeatherData]
      .writeStream.queryName("displaying daily").outputMode("update").foreach(new InfluxDBSinkForEachKillrweatherDaily()).start
*/
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
    // testing
    val monthlyQuery = monthly.writeStream
      .outputMode("update")
      .format("console")
      .start
*/
    /* Saves the monthly data to Cassandra - daily tables. */
    monthly.writeStream
      .format("com.lightbend.killrweather.app.structured.cassandra.streaming.CassandraMonthlySinkProvider")
      .outputMode("update")
      .queryName("DataToCassandraMontlyStreamSinkProvider")
      .start()

    monthly.writeStream
      .format("com.lightbend.killrweather.app.structured.influxDB.streaming.InfluxDBMonthlySinkProvider")
      .outputMode("update")
      .queryName("DataToInfluxDBMonthlyStreamSinkProvider")
      .start()

    /*
    monthly.select("wsid", "year", "month",
      "highTemp", "lowTemp", "meanTemp", "varianceTemp", "stdevTemp",
      "highWind", "lowWind", "meanWind", "varianceWind", "stdevWind",
      "highPressure", "lowPressure", "meanPressure", "variancePressure", "stdevPressure",
      "highPrecip", "lowPrecip", "meanPrecip", "variancePrecip", "stdevPrecip").as[MonthlyWeatherData]
      .writeStream.queryName("saving monthly").outputMode("update").foreach(new CassandraSinkForEachKillrweatherMonthly(spark)).start

    /* Saves the monthly data to Influx - visualization */
    monthly.select("wsid", "year", "month",
      "highTemp", "lowTemp", "meanTemp", "varianceTemp", "stdevTemp",
      "highWind", "lowWind", "meanWind", "varianceWind", "stdevWind",
      "highPressure", "lowPressure", "meanPressure", "variancePressure", "stdevPressure",
      "highPrecip", "lowPrecip", "meanPrecip", "variancePrecip", "stdevPrecip" ).as[MonthlyWeatherData]
      .writeStream.queryName("displaying monthly").outputMode("update").foreach(new InfluxDBSinkForEachKillrweatherMonthly()).start
*/
    //Wait for all streams to finish
    spark.streams.awaitAnyTermination()
  }
}