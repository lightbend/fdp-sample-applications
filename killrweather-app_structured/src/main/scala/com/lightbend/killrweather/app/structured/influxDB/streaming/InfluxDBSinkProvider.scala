package com.lightbend.killrweather.app.structured.influxDB.streaming

import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.sources.StreamSinkProvider
import org.apache.spark.sql.streaming.OutputMode

class InfluxDBRawSinkProvider extends StreamSinkProvider {
  override def createSink(
    sqlContext: SQLContext,
    parameters: Map[String, String],
    partitionColumns: Seq[String],
    outputMode: OutputMode
  ): InfluxDBRawSink = {
    new InfluxDBRawSink(sqlContext)
  }
}

class InfluxDBDailySinkProvider extends StreamSinkProvider {
  override def createSink(
    sqlContext: SQLContext,
    parameters: Map[String, String],
    partitionColumns: Seq[String],
    outputMode: OutputMode
  ): InfluxDBDailySink = {
    new InfluxDBDailySink(sqlContext)
  }
}

class InfluxDBMonthlySinkProvider extends StreamSinkProvider {
  override def createSink(
    sqlContext: SQLContext,
    parameters: Map[String, String],
    partitionColumns: Seq[String],
    outputMode: OutputMode
  ): InfluxDBRMonthlySink = {
    new InfluxDBRMonthlySink(sqlContext)
  }
}