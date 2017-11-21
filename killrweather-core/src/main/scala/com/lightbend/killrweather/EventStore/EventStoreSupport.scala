package com.lightbend.killrweather.EventStore

import com.ibm.event.catalog.{ColumnOrder, IndexSpecification, SortSpecification, TableSchema}
import com.ibm.event.common.ConfigurationReader
import com.ibm.event.oltp.EventContext
import org.apache.spark.sql.types._

object EventStoreSupport {
/*
  val weather_station = TableSchema("rweather_station", StructType(Array(
    StructField("id", StringType, nullable = false),
    StructField("name", StringType, nullable = false),
    StructField("country_code", StringType, nullable = false),
    StructField("state_cod", StringType, nullable = false),
    StructField("call_sign", StringType, nullable = false),
    StructField("lat", DoubleType, nullable = false),
    StructField("long", DoubleType, nullable = false),
    StructField("elevation", DoubleType, nullable = false)
    )),
    shardingColumns = Seq("id"),
    pkColumns = Seq("id")
  )
*/
  val raw_weather_data = TableSchema("raw_weather_data", StructType(Array(
    StructField("wsid", StringType, nullable = false),
    StructField("year", IntegerType, nullable = false),
    StructField("month", IntegerType, nullable = false),
    StructField("day", IntegerType, nullable = false),
    StructField("hour", IntegerType, nullable = false),
    StructField("temperature", DoubleType, nullable = false),
    StructField("dewpoint", DoubleType, nullable = false),
    StructField("pressure", DoubleType, nullable = false),
    StructField("wind_direction", IntegerType, nullable = false),
    StructField("wind_speed", DoubleType, nullable = false),
    StructField("sky_condition", IntegerType, nullable = false),
    StructField("sky_condition_text", StringType, nullable = false),
    StructField("one_hour_precip", DoubleType, nullable = false),
    StructField("six_hour_precip", DoubleType, nullable = false)
  )),
    shardingColumns = Seq("year", "month", "day"),
    pkColumns = Seq("year", "month", "day", "hour")
  )
/*
  val indexSpec = IndexSpecification("pkindex",
    raw_weather_data,
    equalColumns= Seq("deviceId","metricId"),
    sortColumns=Seq(SortSpecification("timeStamp", ColumnOrder.AscendingNullsLast)),
    includeColumns=Seq("metricValue"))
*/
  val sky_condition_lookup = TableSchema("sky_condition_lookup", StructType(Array(
    StructField("code", IntegerType, nullable = false),
    StructField("condition", StringType, nullable = false)
  )),
    shardingColumns = Seq("code"),
    pkColumns = Seq("code")
  )

  val daily_aggregate_temperature = TableSchema("daily_aggregate_temperature", StructType(Array(
    StructField("wsid", StringType, nullable = false),
    StructField("year", IntegerType, nullable = false),
    StructField("month", IntegerType, nullable = false),
    StructField("day", IntegerType, nullable = false),
    StructField("high", DoubleType, nullable = false),
    StructField("low", DoubleType, nullable = false),
    StructField("mean", DoubleType, nullable = false),
    StructField("variance", DoubleType, nullable = false),
    StructField("stdev", DoubleType, nullable = false)
  )),
    shardingColumns = Seq("year", "month"),
    pkColumns = Seq("year", "month", "day")
  )

  val daily_aggregate_windspeed = TableSchema("daily_aggregate_windspeed", StructType(Array(
    StructField("wsid", StringType, nullable = false),
    StructField("year", IntegerType, nullable = false),
    StructField("month", IntegerType, nullable = false),
    StructField("day", IntegerType, nullable = false),
    StructField("high", DoubleType, nullable = false),
    StructField("low", DoubleType, nullable = false),
    StructField("mean", DoubleType, nullable = false),
    StructField("variance", DoubleType, nullable = false),
    StructField("stdev", DoubleType, nullable = false)
  )),
    shardingColumns = Seq("year", "month"),
    pkColumns = Seq("year", "month", "day")
  )


  val daily_aggregate_pressure = TableSchema("daily_aggregate_pressure", StructType(Array(
    StructField("wsid", StringType, nullable = false),
    StructField("year", IntegerType, nullable = false),
    StructField("month", IntegerType, nullable = false),
    StructField("day", IntegerType, nullable = false),
    StructField("high", DoubleType, nullable = false),
    StructField("low", DoubleType, nullable = false),
    StructField("mean", DoubleType, nullable = false),
    StructField("variance", DoubleType, nullable = false),
    StructField("stdev", DoubleType, nullable = false)
  )),
    shardingColumns = Seq("year", "month"),
    pkColumns = Seq("year", "month", "day")
  )

  val daily_aggregate_precip = TableSchema("daily_aggregate_precip", StructType(Array(
    StructField("wsid", StringType, nullable = false),
    StructField("year", IntegerType, nullable = false),
    StructField("month", IntegerType, nullable = false),
    StructField("day", IntegerType, nullable = false),
    StructField("precipitation", DoubleType, nullable = false)
  )),
    shardingColumns = Seq("year", "month"),
    pkColumns = Seq("year", "month", "day")
  )

  val monthly_aggregate_temperature = TableSchema("monthly_aggregate_temperature", StructType(Array(
    StructField("wsid", StringType, nullable = false),
    StructField("year", IntegerType, nullable = false),
    StructField("month", IntegerType, nullable = false),
    StructField("high", DoubleType, nullable = false),
    StructField("low", DoubleType, nullable = false),
    StructField("mean", DoubleType, nullable = false),
    StructField("variance", DoubleType, nullable = false),
    StructField("stdev", DoubleType, nullable = false)
  )),
    shardingColumns = Seq("year"),
    pkColumns = Seq("year", "month")
  )

  val monthly_aggregate_windspeed = TableSchema("monthly_aggregate_windspeed", StructType(Array(
    StructField("wsid", StringType, nullable = false),
    StructField("year", IntegerType, nullable = false),
    StructField("month", IntegerType, nullable = false),
    StructField("high", DoubleType, nullable = false),
    StructField("low", DoubleType, nullable = false),
    StructField("mean", DoubleType, nullable = false),
    StructField("variance", DoubleType, nullable = false),
    StructField("stdev", DoubleType, nullable = false)
  )),
    shardingColumns = Seq("year"),
    pkColumns = Seq("year", "month")
  )

  val monthly_aggregate_pressure = TableSchema("monthly_aggregate_pressure", StructType(Array(
    StructField("wsid", StringType, nullable = false),
    StructField("year", IntegerType, nullable = false),
    StructField("month", IntegerType, nullable = false),
    StructField("high", DoubleType, nullable = false),
    StructField("low", DoubleType, nullable = false),
    StructField("mean", DoubleType, nullable = false),
    StructField("variance", DoubleType, nullable = false),
    StructField("stdev", DoubleType, nullable = false)
  )),
    shardingColumns = Seq("year"),
    pkColumns = Seq("year", "month")
  )

  val monthly_aggregate_precip = TableSchema("monthly_aggregate_precip", StructType(Array(
    StructField("wsid", StringType, nullable = false),
    StructField("year", IntegerType, nullable = false),
    StructField("month", IntegerType, nullable = false),
    StructField("high", DoubleType, nullable = false),
    StructField("low", DoubleType, nullable = false),
    StructField("mean", DoubleType, nullable = false),
    StructField("variance", DoubleType, nullable = false),
    StructField("stdev", DoubleType, nullable = false)
  )),
    shardingColumns = Seq("year"),
    pkColumns = Seq("year", "month")
  )

  val tables = Map(//"weather_station" -> weather_station,
                "raw_weather_data" -> raw_weather_data,
                "sky_condition_lookup" -> sky_condition_lookup,
                "daily_aggregate_temperature" -> daily_aggregate_temperature,
                "daily_aggregate_windspeed" -> daily_aggregate_windspeed,
                "daily_aggregate_pressure" -> daily_aggregate_pressure,
                "daily_aggregate_precip" -> daily_aggregate_precip,
                "monthly_aggregate_temperature" -> monthly_aggregate_temperature,
                "monthly_aggregate_windspeed" -> monthly_aggregate_windspeed,
                "monthly_aggregate_pressure" -> monthly_aggregate_pressure,
                "monthly_aggregate_precip" -> monthly_aggregate_precip
  )


  val dbName = "KillrWeather"
  val eventStoreIP = "127.0.0.1"
  val eventStorePort = "5555"

  def createContext() : EventContext = {
    ConfigurationReader.setConnectionEndpoints(s"$eventStoreIP:$eventStorePort")
    try{
      EventContext.createDatabase(dbName)
    }
    catch {
      case e: Throwable => {
        EventContext.openDatabase(dbName)
        EventContext.getEventContext
      }
    }
  }

  def ensureTables(ctx : EventContext) : Unit = {
    val existing = ctx.getNamesOfTables.toList
    println(s"Tables : ${existing.mkString(",")}")
    tables foreach(tabDef => {
      if(!existing.contains(tabDef._1)) {
        ctx.createTable(tabDef._2)
        println(s"Table ${tabDef._1} created")
      }
      else
        println(s"Table ${tabDef._1} exist")
    })
  }
}