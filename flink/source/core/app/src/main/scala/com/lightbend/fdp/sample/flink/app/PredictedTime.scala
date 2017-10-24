package com.lightbend.fdp.sample.flink.app

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.streaming.util.serialization.{DeserializationSchema, SerializationSchema}

case class PredictedTime(rideId: Long, predictedTimeInMins: Int) {
  override def toString() = s"$rideId,$predictedTimeInMins"
}

object PredictedTime {
  def fromString(str: String): PredictedTime = {
    val arr = str.split(",")
    if (arr.length != 2) throw new Exception(s"Invalid source string for deserialization $str")
    else PredictedTime(arr(0).toLong, arr(1).toInt)
  }
}

class PredictedTimeSchema extends DeserializationSchema[PredictedTime] with SerializationSchema[PredictedTime] {
  override def serialize(element: PredictedTime): Array[Byte] = element.toString.getBytes

  override def deserialize(message: Array[Byte]): PredictedTime = PredictedTime.fromString(new String(message, "UTF-8"))

  override def isEndOfStream(nextElement: PredictedTime) = false

  override def getProducedType(): TypeInformation[PredictedTime] = TypeExtractor.getForClass(classOf[PredictedTime])
}
