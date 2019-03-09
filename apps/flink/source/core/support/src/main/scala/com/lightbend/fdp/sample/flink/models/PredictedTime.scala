package com.lightbend.fdp.sample.flink.models

import scala.util.Try

case class PredictedTime(rideId: Long, predictedTimeInMins: Int) {
  override def toString() = s"$rideId,$predictedTimeInMins"
}

object PredictedTime {
  def fromString(str: String): Try[PredictedTime] = Try{
    val arr = str.split(",")
    if (arr.length < 2) throw new Exception(s"Invalid source string for deserialization $str")
    else try {
      PredictedTime(arr(0).toLong, arr(1).toInt)
    } catch {
      case t: Throwable =>
        throw new RuntimeException(s"Invalid record: $str. Error $t")
    }
  }
}
