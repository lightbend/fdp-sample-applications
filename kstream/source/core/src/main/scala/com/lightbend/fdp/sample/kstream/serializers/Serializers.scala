package com.lightbend.fdp.sample.kstream
package serializers

import models.LogRecord
import org.apache.kafka.streams.kstream.Windowed
import org.apache.kafka.common.serialization.{ Serde, Serdes, StringSerializer, StringDeserializer }
import org.apache.kafka.streams.kstream.internals.{ WindowedSerializer, WindowedDeserializer }
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig

trait Serializers {
  final val ts = new Tuple2Serializer[String, String]()
  final val ms = new ModelSerializer[LogRecord]()
  final val logRecordSerde = Serdes.serdeFrom(ms, ms)
  final val tuple2StringSerde = Serdes.serdeFrom(ts, ts)
  final val stringSerializer = new StringSerializer()
  final val stringDeserializer = new StringDeserializer()
  final val windowedSerializer: WindowedSerializer[String] = new WindowedSerializer[String](stringSerializer)
  final val windowedDeserializer: WindowedDeserializer[String] = new WindowedDeserializer[String](stringDeserializer)
  final val windowedSerde: Serde[Windowed[String]] = Serdes.serdeFrom(windowedSerializer, windowedDeserializer)
  final val stringSerde = Serdes.String()
  final val longSerde = Serdes.Long()
  final val byteArraySerde = Serdes.ByteArray()

  /**
   * The Serde instance varies depending on whether we are using Schema Registry. If we are using
   * schema registry, we use the serde provided by Confluent, else we use Avro serialization backed by
   * Twitter's bijection library
   */ 
  def logRecordAvroSerde(maybeSchemaRegistryUrl: Option[String]) = maybeSchemaRegistryUrl.map { url =>
    val serde = new SpecificAvroSerdeWithSchemaRegistry[LogRecordAvro]()
    serde.configure(
        java.util.Collections.singletonMap(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, url),
        false)
    serde
  }.getOrElse {
    new SpecificAvroSerde[LogRecordAvro](LogRecordAvro.SCHEMA$)
  }
}
