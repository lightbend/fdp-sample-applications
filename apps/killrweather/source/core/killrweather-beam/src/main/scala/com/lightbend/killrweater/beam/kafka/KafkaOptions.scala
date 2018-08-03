package com.lightbend.killrweater.beam.kafka

import org.apache.beam.runners.direct.DirectOptions
import org.apache.beam.sdk.options.{Default, Description}

trait KafkaOptions extends DirectOptions {

  @Description("The Kafka topic to read data from")
  @Default.String("killrweather.raw") def getKafkaDataTopic: String

  def setKafkaDataTopic(value: String): Unit

  @Description("The Kafka Broker to read from")
  @Default.String("localhost:9092") def getBroker: String

  def setBroker(value: String): Unit

  @Description("The Data Reading groupId")
  @Default.String("killrweather.group") def getDataGroup: String

  def setDataGroup(value: String): Unit
}