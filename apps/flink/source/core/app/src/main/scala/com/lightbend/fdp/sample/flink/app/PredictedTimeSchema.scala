package com.lightbend.fdp.sample.flink.app

import com.lightbend.fdp.sample.flink.models.PredictedTime
import org.apache.flink.api.common.serialization.{DeserializationSchema, SerializationSchema}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor

class PredictedTimeSchema extends DeserializationSchema[PredictedTime] with SerializationSchema[PredictedTime] {
  override def serialize(element: PredictedTime): Array[Byte] = element.toString.getBytes

  override def deserialize(message: Array[Byte]): PredictedTime = PredictedTime.fromString(new String(message, "UTF-8")).get

  override def isEndOfStream(nextElement: PredictedTime) = false

  override def getProducedType(): TypeInformation[PredictedTime] = TypeExtractor.getForClass(classOf[PredictedTime])
}
