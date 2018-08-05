package com.lightbend.killrweater.beam.processors

import com.lightbend.killrweather.influxdb.InfluxSetup
import org.apache.beam.sdk.transforms.DoFn
import org.apache.beam.sdk.transforms.DoFn.{ProcessElement, Setup, Teardown}
import org.apache.beam.sdk.values.KV
import org.influxdb.InfluxDB
import org.influxdb.dto.Point


// Pardo is well described at http://www.waitingforcode.com/apache-beam/pardo-transformation-apache-beam/read

class WriteToInfluxDBFn[InputT](convertData : KV[String, InputT] => Point) extends DoFn[KV[String, InputT], Unit] {

  val MAXATTEMPTS = 3

  private var influxDB : InfluxDB = null

  @Setup
  def connect() : Unit = {
    var connected = false
    var attempts = 0
    while(!connected && (attempts < MAXATTEMPTS)) {
      try {
        influxDB = InfluxSetup.setup()
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