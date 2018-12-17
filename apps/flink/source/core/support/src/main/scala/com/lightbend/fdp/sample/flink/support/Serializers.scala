package com.lightbend.fdp.sample.flink.support

import org.apache.kafka.common.serialization.{Serdes, StringDeserializer, StringSerializer}

trait Serializers {
  final val stringSerializer = new StringSerializer()
  final val stringDeserializer = new StringDeserializer()
  final val stringSerde = Serdes.String()
  final val longSerde = Serdes.Long()
  final val byteArraySerde = Serdes.ByteArray()
}
