package com.lightbend.killrweater.beam.processors

import java.util.concurrent.TimeUnit

import com.lightbend.killrweather.settings.WeatherSettings
import org.apache.beam.sdk.transforms.DoFn
import org.apache.beam.sdk.transforms.DoFn.{ProcessElement, Setup, Teardown}
import org.apache.beam.sdk.values.KV
import org.influxdb.{InfluxDB, InfluxDBFactory}
import org.influxdb.dto.Point

// Pardo is well described at http://www.waitingforcode.com/apache-beam/pardo-transformation-apache-beam/read

class WriteToInfluxDBFn[InputT](convertData : KV[String, InputT] => Point) extends DoFn[KV[String, InputT], Unit] {

  val MAXATTEMPTS = 3
  val killrSettings = WeatherSettings("KillrWeather", new Array[String](0))
  import killrSettings._

  private var influxDB : InfluxDB = null

  @Setup
  def connect() : Unit = {
    var connected = false
    var attempts = 0
    while(!connected && (attempts < MAXATTEMPTS)) {
      try {
        influxDB = InfluxDBFactory.connect(s"${influxConfig.server}:${influxConfig.port}", influxConfig.user, influxConfig.password)
        if (!influxDB.databaseExists(influxTableConfig.database)) {
          influxDB.createDatabase(influxTableConfig.database)
          influxDB.dropRetentionPolicy("autogen", influxTableConfig.database)
          influxDB.createRetentionPolicy(influxTableConfig.retentionPolicy, influxTableConfig.database, "1d", "30m", 1, true)
        }

        influxDB.setDatabase(influxTableConfig.database)
        // Flush every 2000 Points, at least every 100ms
        influxDB.enableBatch(2000, 100, TimeUnit.MILLISECONDS)
        // set retention policy
        influxDB.setRetentionPolicy(influxTableConfig.retentionPolicy)
        connected = true
      }
      catch {
        case t: Throwable => {
            println(s"Exception connecting to Influx $t")
            teardown()
            Thread.sleep(100)
            attempts = attempts + 1
        }
      }
    }
  }


  @ProcessElement
  def processElement(ctx: DoFn[KV[String, InputT], Unit]#ProcessContext): Unit = {
    write(convertData(ctx.element()))
  }

  @Teardown
  def teardown() : Unit = {
    if (influxDB != null) {
      try {
        influxDB.flush()
      }
      catch { case t: Throwable => }
      try{
        influxDB.close()
      }
      catch { case t: Throwable => }
      influxDB = null
    }
  }

  private def write(point: Point): Unit = {
    try {
      influxDB.write(point)
    } catch {
      case t: Throwable => {
        println(s"Exception writing to Influx $t")
        teardown()
        connect()
      }
    }
  }
}