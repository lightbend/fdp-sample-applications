package com.lightbend.killrweather.app.structured.cassandra.streaming

import org.apache.spark.sql.sources.StreamSinkProvider
import org.apache.spark.sql.streaming.OutputMode
import org.apache.spark.sql.SQLContext

/**
 * From Holden Karau's High Performance Spark
 * https://github.com/holdenk/spark-structured-streaming-ml/blob/master/src/main/scala/com/high-performance-spark-examples/structuredstreaming/CustomSink.scala#L66
 *
 */
class CassandraRawSinkProvider extends StreamSinkProvider {
  override def createSink(
    sqlContext: SQLContext,
    parameters: Map[String, String],
    partitionColumns: Seq[String],
    outputMode: OutputMode
  ): CassandraRawSink = {
    new CassandraRawSink(sqlContext)
  }
}

class CassandraDailySinkProvider extends StreamSinkProvider {
  override def createSink(
    sqlContext: SQLContext,
    parameters: Map[String, String],
    partitionColumns: Seq[String],
    outputMode: OutputMode
  ): CassandraDailySink = {
    new CassandraDailySink(sqlContext)
  }
}

class CassandraMonthlySinkProvider extends StreamSinkProvider {
  override def createSink(
    sqlContext: SQLContext,
    parameters: Map[String, String],
    partitionColumns: Seq[String],
    outputMode: OutputMode
  ): CassandraMonthlySink = {
    new CassandraMonthlySink(sqlContext)
  }
}