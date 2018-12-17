package com.lightbend.fdp.sample.flink.app

import com.lightbend.fdp.sample.flink.models.TaxiRide
import org.apache.flink.api.common.serialization.{DeserializationSchema, SerializationSchema}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor

class TaxiRideSchema extends DeserializationSchema[TaxiRide] with SerializationSchema[TaxiRide] {
  override def serialize(element: TaxiRide): Array[Byte] = element.toString.getBytes

  override def deserialize(message: Array[Byte]): TaxiRide = TaxiRide.fromString(new String(message))

  override def isEndOfStream(nextElement: TaxiRide) = false

  override def getProducedType: TypeInformation[TaxiRide] = TypeExtractor.getForClass(classOf[TaxiRide])
}
