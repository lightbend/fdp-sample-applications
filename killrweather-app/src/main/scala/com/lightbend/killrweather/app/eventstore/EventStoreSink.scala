package com.lightbend.killrweather.app.eventstore

import com.ibm.event.oltp.EventContext
import com.lightbend.killrweather.EventStore.EventStoreSupport
import org.apache.spark.sql.Row

object EventStoreSink {

  def apply(): EventStoreSink = {
    val f = () => {
      val ctx = EventStoreSupport.createContext()
      EventStoreSupport.ensureTables(ctx)
      sys.addShutdownHook {
        EventContext.cleanUp()
      }
      ctx
    }
    new EventStoreSink(f)
  }
}

class EventStoreSink(createContext: () => EventContext) extends Serializable {

  lazy val ctx = createContext()

  def writeRaw(raw: Iterator[Row]): Unit = {
    val table = ctx.getTable("raw_weather_data")
    raw.foreach(ctx.insert(table, _))
  }

  def writeDailyTemperature(raw: Iterator[Row]): Unit = {
    val table = ctx.getTable("daily_aggregate_temperature")
    raw.foreach(ctx.insert(table, _))
  }

  def writeDailyWind(raw: Iterator[Row]): Unit = {
    val table = ctx.getTable("daily_aggregate_windspeed")
    raw.foreach(ctx.insert(table, _))
  }

  def writeDailyPressure(raw: Iterator[Row]): Unit = {
    val table = ctx.getTable("daily_aggregate_pressure")
    raw.foreach(ctx.insert(table, _))
  }

  def writeDailyPresip(raw: Iterator[Row]): Unit = {
    val table = ctx.getTable("daily_aggregate_precip")
    raw.foreach(ctx.insert(table, _))
  }

  def writeMothlyTemperature(raw: Iterator[Row]): Unit = {
    val table = ctx.getTable("monthly_aggregate_temperature")
    raw.foreach(ctx.insert(table, _))
  }

  def writeMothlyWind(raw: Iterator[Row]): Unit = {
    val table = ctx.getTable("monthly_aggregate_windspeed")
    raw.foreach(ctx.insert(table, _))
  }

  def writeMothlyPressure(raw: Iterator[Row]): Unit = {
    val table = ctx.getTable("monthly_aggregate_pressure")
    raw.foreach(ctx.insert(table, _))
  }

  def writeMothlyPresip(raw: Iterator[Row]): Unit = {
    val table = ctx.getTable("monthly_aggregate_precip")
    raw.foreach(ctx.insert(table, _))
  }
}