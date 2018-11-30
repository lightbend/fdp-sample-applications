package com.lightbend.fdp.sample.flink.ingestion

import org.apache.kafka.common.serialization.{ Serde, Serdes, StringSerializer, StringDeserializer }

trait Serializers {
  final val stringSerializer = new StringSerializer()
  final val stringDeserializer = new StringDeserializer()
  final val stringSerde = Serdes.String()
  final val longSerde = Serdes.Long()
  final val byteArraySerde = Serdes.ByteArray()
}



