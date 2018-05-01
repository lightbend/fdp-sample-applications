package com.lightbend.killrweather.influxdb

import java.util.concurrent.TimeUnit

import com.lightbend.killrweather.settings.WeatherSettings
import org.influxdb.{InfluxDBFactory, InfluxDB}
import org.influxdb.dto.Query
import collection.JavaConverters._

object InfluxSetup {

  val settings = WeatherSettings()
  import settings._

  def setup() : InfluxDB = {
    val influxDB = InfluxDBFactory.connect(influxConfig.url, influxConfig.user, influxConfig.password)
    val databasesQuery = new Query("SHOW DATABASES","")
    val database_exists = influxDB.query(databasesQuery).getResults.get(0).getSeries.get(0).getValues match {
      case databases if databases == null => false
      case databases =>
        val names = databases.asScala.map(_.get(0).toString())
        if(names.contains(influxTableConfig.database)) true else false
    }
    if(!database_exists){
      val databaseCreateQuery = new Query(s"""CREATE DATABASE "${influxTableConfig.database}"""","")
      influxDB.query(databaseCreateQuery)
      val dropRetentionQuery = new Query(s"""DROP RETENTION POLICY "autogen" ON "${influxTableConfig.database}" ""","")
      influxDB.query(dropRetentionQuery)
      val createRetentionQuery = new Query(s"""CREATE RETENTION POLICY "${influxTableConfig.retentionPolicy}" ON "${influxTableConfig.database}" DURATION 1d REPLICATION 1 SHARD DURATION 30m  DEFAULT""","")
      influxDB.query(createRetentionQuery)
    }

    influxDB.setDatabase(influxTableConfig.database)
    // Flush every 2000 Points, at least every 100ms
    influxDB.enableBatch(2000, 100, TimeUnit.MILLISECONDS)
    // set retention policy
    influxDB.setRetentionPolicy(influxTableConfig.retentionPolicy)
    influxDB
  }
}
