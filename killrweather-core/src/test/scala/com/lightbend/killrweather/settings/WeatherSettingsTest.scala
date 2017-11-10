package com.lightbend.killrweather.settings

import org.scalatest.{ Matchers, WordSpec }

class WeatherSettingsTest extends WordSpec with Matchers {

  "WeatherSettings" should {
    "Load the default Kafka configuration" in {
      val ws = new WeatherSettings()
      ws.kafkaBrokers should be("unit-test-host") // from test override
      ws.KafkaGroupId should be("killrweather.group") // from reference
      ws.KafkaTopicRaw should be("killrweather.raw") // from reference
    }

    "Load the default Spark configuration" in {
      val ws = new WeatherSettings()
      val sparkConf = ws.sparkConfig
      sparkConf should contain("spark.master" -> "local[2]")
    }

    "Load the default streaming configuration" in {
      val ws = new WeatherSettings()
      ws.SparkCheckpointDir should include("checkpoints")
      ws.SparkStreamingBatchInterval.getSeconds should be > 1L
    }

  }

}
