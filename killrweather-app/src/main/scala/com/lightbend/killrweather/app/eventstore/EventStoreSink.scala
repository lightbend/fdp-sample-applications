package com.lightbend.killrweather.app.eventstore

import com.ibm.event.oltp.EventContext
import com.lightbend.killrweather.EventStore.EventStoreSupport
import com.lightbend.killrweather.WeatherClient.WeatherRecord
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

class EventStoreSink (createContext: () => EventContext) extends Serializable {

  lazy val ctx = createContext()

  def write(raw: WeatherRecord): Unit = {
    val table = ctx.getTable("raw_weather_data")
    ctx.insert(table, Row.apply(raw))
  }
}