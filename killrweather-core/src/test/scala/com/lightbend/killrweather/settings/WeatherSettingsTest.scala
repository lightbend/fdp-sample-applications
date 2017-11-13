package com.lightbend.killrweather.settings

import org.scalatest.{ Matchers, WordSpec }

class WeatherSettingsTest extends WordSpec with Matchers {

  "WeatherSettings" should {
    val ws = new WeatherSettings()

    "Load the default Kafka configuration" in {
      ws.kafkaConfig.brokers should be("unit-test-host") // from test override
      ws.kafkaConfig.group should be("killrweather.group") // from reference
      ws.kafkaConfig.topic should be("killrweather.raw") // from reference
    }

    "Load the default Spark configuration" in {
      val sparkConf = ws.sparkConf
      sparkConf.get("spark.master") should be("local[2]")
    }

    "Load the default streaming configuration" in {
      ws.streamingConfig.checkpointDir should include("checkpoints")
      ws.streamingConfig.batchInterval.toSeconds should be > 1L
    }

    "Load the default application-bound cassandra configuration" in {
      ws.cassandraConfig.keyspace should not be ('empty)
    }

    "Load the default influx db configuration" in {
      ws.influxConfig.server should not be ('empty)
      ws.influxConfig.port should be > 1024
      ws.influxConfig.url should startWith(ws.influxConfig.server)
      ws.influxConfig.url should endWith(ws.influxConfig.port.toString)
    }

    "Load the defauls influx table configuration for the application" in {
      ws.influxTableConfig.database should not be ('empty)
    }

  }

}
