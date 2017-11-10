package com.lightbend.killrweather.settings

import org.scalatest.{ Matchers, WordSpec }

class WeatherSettingsTest extends WordSpec with Matchers {

  "WeatherSettings" should {
    "Load the configuration from the default config files" in {
      val ws = new WeatherSettings()
      ws.kafkaBrokers should be("unit-test-host") // from test override
      ws.KafkaGroupId should be("killrweather.group") // from reference
      ws.KafkaTopicRaw should be("killrweather.raw") // from reference
    }

  }

}
