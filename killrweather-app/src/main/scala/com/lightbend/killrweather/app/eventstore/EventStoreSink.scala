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

  def writeRaw(raw: Row): Unit = {
    val table = ctx.getTable("raw_weather_data")
    ctx.insert(table, raw)
  }

  def writeDailyTemperature(raw: Row): Unit = {
    val table = ctx.getTable("daily_aggregate_temperature")
    ctx.insert(table, raw)
  }

  def writeDailyWind(raw: Row): Unit = {
    val table = ctx.getTable("daily_aggregate_windspeed")
    ctx.insert(table, raw)
  }

  def writeDailyPressure(raw: Row): Unit = {
    val table = ctx.getTable("daily_aggregate_pressure")
    ctx.insert(table, raw)
  }

  def writeDailyPresip(raw: Row): Unit = {
    val table = ctx.getTable("daily_aggregate_precip")
    ctx.insert(table, raw)
  }

  def writeMothlyTemperature(raw: Row): Unit = {
    val table = ctx.getTable("monthly_aggregate_temperature")
    ctx.insert(table, raw)
  }

  def writeMothlyWind(raw: Row): Unit = {
    val table = ctx.getTable("monthly_aggregate_windspeed")
    ctx.insert(table, raw)
  }

  def writeMothlyPressure(raw: Row): Unit = {
    val table = ctx.getTable("monthly_aggregate_pressure")
    ctx.insert(table, raw)
  }

  def writeMothlyPresip(raw: Row): Unit = {
    val table = ctx.getTable("monthly_aggregate_precip")
    ctx.insert(table, raw)
  }

}