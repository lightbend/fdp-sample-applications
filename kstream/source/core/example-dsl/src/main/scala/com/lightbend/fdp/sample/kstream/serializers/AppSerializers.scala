/**
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.fdp.sample.kstream
package serializers

import models.LogRecord
import org.apache.kafka.common.serialization.{ Serdes, Serde }
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import com.lightbend.kafka.scala.iq.serializers._

import org.apache.kafka.streams.kstream.Windowed
import org.apache.kafka.common.serialization._
import org.apache.kafka.streams.kstream.internals.{WindowedDeserializer, WindowedSerializer}
import com.lightbend.kafka.scala.streams.DefaultSerdes.stringSerde

trait AppSerializers { 
  final val ts = new Tuple2Serializer[String, String]()
  final val ms = new ModelSerializer[LogRecord]()
  implicit val logRecordSerde: Serde[LogRecord] = Serdes.serdeFrom(ms, ms)
  implicit val tuple2StringSerde = Serdes.serdeFrom(ts, ts)

  final val windowedStringSerializer: WindowedSerializer[String] = new WindowedSerializer[String](stringSerde.serializer)
  final val windowedStringDeserializer: WindowedDeserializer[String] = new WindowedDeserializer[String](stringSerde.deserializer)
  implicit val windowedStringSerde: Serde[Windowed[String]] = Serdes.serdeFrom(windowedStringSerializer, windowedStringDeserializer)
  

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
