package com.lightbend.fdp.sample.nwintrusion

import java.io.Serializable
import java.util.concurrent.TimeUnit

import org.influxdb.dto.Point
import org.influxdb.{ InfluxDB, InfluxDBFactory }

class InfluxDBSink(createWriter: () => InfluxDB) extends Serializable {

  lazy val influxDB = createWriter()

  def write(value: Double): Unit = {
    val anomalousPoint = 
      Point.measurement("anomalous")
           .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
           .addField("distanceFromCentroid", value)
           .tag("AnomalyTag", "AnomalyTagValue")
           .build
    
    write(anomalousPoint)
  }

  private def write(point: Point): Unit = {
    try {
      influxDB.write(point)
    } catch { case t: Throwable => 
      println(s"Exception writing to Influx") 
      t.printStackTrace
    }
  }
}

object InfluxDBSink {

  def apply(c: InfluxConfig.ConfigData): InfluxDBSink = {
    val f = () => {
      import c._

      val influxDB = InfluxDBFactory.connect(s"$server:$port", user, password)
      if (!influxDB.databaseExists(database)) {
        println(s"Database does not exist - going to create one ..")
        influxDB.createDatabase(database)
      }

      influxDB.setDatabase(database)
      // Flush every 2000 Points, at least every 100ms
      influxDB.enableBatch(2000, 100, TimeUnit.MILLISECONDS)

      // drop the default retention policy which has infinite duration
      influxDB.dropRetentionPolicy("autogen", database)

      // could make all these configurable through influx.conf
      // The problem is there is no validation of the string values in the Java client
      // hence need to do all validations in config reading, which will be quite exhaustive
      // Maybe we revisit this later
      influxDB.createRetentionPolicy(retentionPolicy, database, "1d", "30m", 1, true)
      // set retention policy
      influxDB.setRetentionPolicy(retentionPolicy)

      sys.addShutdownHook {
        influxDB.flush()
        influxDB.close()
      }
      influxDB
    }

    // setup Grafana
    new GrafanaSetup(c).setGrafana

    new InfluxDBSink(f)
  }
}
