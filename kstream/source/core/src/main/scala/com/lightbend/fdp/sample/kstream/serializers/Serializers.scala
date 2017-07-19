package com.lightbend.fdp.sample.kstream
package serializers

import models.LogRecord
import org.apache.kafka.streams.kstream.Windowed
import org.apache.kafka.common.serialization.{ Serde, Serdes, StringSerializer, StringDeserializer }
import org.apache.kafka.streams.kstream.internals.{ WindowedSerializer, WindowedDeserializer }

trait Serializers {
  final val ts = new Tuple2Serializer[String, String]()
  final val ms = new ModelSerializer[LogRecord]()
  final val logRecordSerde = Serdes.serdeFrom(ms, ms)
  final val logRecordAvroSerde = new SpecificAvroSerde[LogRecordAvro]()
  final val tuple2StringSerde = Serdes.serdeFrom(ts, ts)
  final val stringSerializer = new StringSerializer()
  final val stringDeserializer = new StringDeserializer()
  final val windowedSerializer: WindowedSerializer[String] = new WindowedSerializer[String](stringSerializer)
  final val windowedDeserializer: WindowedDeserializer[String] = new WindowedDeserializer[String](stringDeserializer)
  final val windowedSerde: Serde[Windowed[String]] = Serdes.serdeFrom(windowedSerializer, windowedDeserializer)
  final val stringSerde = Serdes.String()
  final val longSerde = Serdes.Long()
  final val byteArraySerde = Serdes.ByteArray()
}
