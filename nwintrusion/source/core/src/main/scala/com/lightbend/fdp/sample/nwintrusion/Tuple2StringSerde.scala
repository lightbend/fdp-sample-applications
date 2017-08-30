package com.lightbend.fdp.sample.nwintrusion

import org.apache.kafka.common.serialization.{ Serde, Deserializer, Serializer }

import java.nio.charset.Charset

class Tuple2StringSerde extends Serializer[(String, String)] 
  with Deserializer[(String, String)] with Serde[(String, String)] {

  final val CHARSET = Charset.forName("UTF-8")

  override def configure(configs: java.util.Map[String, _], isKey: Boolean) = {}

  override def serialize(topic: String, data: (String, String)) =
    s"${data._1}/${data._2}".getBytes(CHARSET)

  override def deserialize(topic: String, bytes: Array[Byte]) = {
    val arr = new String(bytes, CHARSET).split("/")
    (arr(0), arr(1))
  }

  override def close() = {}

  override def serializer() = new Tuple2StringSerde
  override def deserializer() = new Tuple2StringSerde
}
