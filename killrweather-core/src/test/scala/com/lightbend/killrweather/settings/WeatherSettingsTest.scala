package com.lightbend.killrweather.settings

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{ Matchers, WordSpec }

class WeatherSettingsTest extends WordSpec with Matchers with TableDrivenPropertyChecks {

  "Config driver WeatherSettings" should {
    val ws = WeatherSettings()

    "Load the default Kafka configuration" in {
      ws.kafkaConfig.brokers should be("unit-test-host") // from test override
      ws.kafkaConfig.group should be("killrweather.group") // from reference
      ws.kafkaConfig.topic should be("killrweather.raw") // from reference
    }

    "Load the default Spark configuration" in {
      val sparkConf = ws.sparkConf
      sparkConf.get("spark.master") should be("local[2]")
    }

    "Load the default cassandra server configuration" in {
      ws.cassandraServerConfig.host should be ("localhost")
      ws.cassandraServerConfig.port should be (9042)
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

  "WeatherSettings with overrides" should {
    val settings: Array[String] => WeatherSettings = arr => WeatherSettings("app", arr)
    "override WeatherConfig settings through arguments" in {
      settings(Array("--kafka-brokers", "overridden")).kafkaConfig.brokers should be("overridden")
      settings(Array("--with-influxdb")).influxConfig.enabled should be(true)
      settings(Array("--without-influxdb")).influxConfig.enabled should be(false)
      settings(Array("--cassandra-hosts", "h1,h2")).sparkConf().get("spark.cassandra.connection.host") should be("h1,h2")
    }
    "ignore unknown arguments" in {
      settings(Array("fooarg")) should be(WeatherSettings())
    }

  }

}
