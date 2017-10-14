package com.lightbend.killrweather.app.structured

import com.lightbend.killrweather.WeatherClient.WeatherRecord
import com.lightbend.killrweather.settings.WeatherSettings
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._


// https://github.com/polomarcus/Spark-Structured-Streaming-Examples

object KillrWeatherStructured {

  def main(args: Array[String]): Unit = {

    // Create context

    WeatherSettings.handleArgs("KillrWeather", args)
    val settings = new WeatherSettings()
    import settings._

    val spark = SparkSession.builder
      .appName("KillrWeather with Structured Streaming")
      .master("local")
      .config("spark.cassandra.connection.host", CassandraHosts)
      .config("spark.sql.streaming.checkpointLocation", SparkCheckpointDir)
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .getOrCreate()

    // Message parsing
    spark.udf.register("deserialize", (data: Array[Byte]) => WeatherRecord.parseFrom(data))

    // Read raw data
    val binaryRaw = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "10.2.2.221:1025" /*kafkaBrokers*/)
      .option("subscribe", KafkaTopicRaw)
      .option("enable.auto.commit", false) // Cannot be set to true in Spark Strucutured Streaming https://spark.apache.org/docs/latest/structured-streaming-kafka-integration.html#kafka-specific-configurations
      .option("group.id", KafkaGroupId + ".structured")
      .option("startingOffsets", "earliest")
      .option("failOnDataLoss", "false")
      .load()

    // Convert to weather message

    val parsedRaw = binaryRaw.selectExpr("""deserialize(value) AS message""").select("message")
      .select("message.wsid","message.year", "message.month","message.day", "message.hour", "message.temperature",
        "message.dewpoint","message.pressure", "message.windDirection", "message.windSpeed", "message.skyCondition",
        "message.skyConditionText", "message.oneHourPrecip", "message.sixHourPrecip")

    // testing
    val rawQuery = parsedRaw.writeStream
      .outputMode("update")
      .format("console")
      .start()


    /** Saves the raw data to Cassandra - raw table. */
//    parsedRaw.rdd.saveToCassandra(CassandraKeyspace, CassandraTableRaw)

    // Calculate daily
    val daily = parsedRaw
        .groupBy("wsid","year","month","day")
        .agg(
          max("temperature")as("highTemp"),min("temperature")as("lowTemp"),avg("temperature")as("meanTemp"),
          max("windSpeed")as("highWind"),min("windSpeed")as("lowWind"),avg("windSpeed")as("meanWind"),
          max("pressure")as("highPressure"),min("pressure")as("lowPressure"),avg("pressure")as("meanPressure"),
          sum("oneHourPrecip").as("precip")
        )

    // Start running the query that prints the running counts to the console
    val dailyQuery = daily.writeStream
      .outputMode("update")
      .format("console")
      .start()


    //Wait for all streams to finish
    spark.streams.awaitAnyTermination()
  }
}
