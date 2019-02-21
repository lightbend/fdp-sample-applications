package com.lightbend.fdp.sample.flink.models

import java.util.Locale

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

/**
  * A TaxiRide is a taxi ride event. There are two types of events, a taxi ride start event and a
  * taxi ride end event. The isStart flag specifies the type of the event.
  *
  * A TaxiRide consists of
  * - the rideId of the event which is identical for start and end record
  * - the time of the event
  * - the longitude of the start location
  * - the latitude of the start location
  * - the longitude of the end location
  * - the latitude of the end location
  * - the passengerCnt of the ride
  * - the travelDistance which is -1 for start events
  *
  */

case class TaxiRide(rideId: Long, isStart: Boolean, startTime: DateTime, endTime: DateTime,
               startLon: Float, startLat: Float, endLon: Float, endLat: Float,
               passengerCnt: Short) extends Comparable[TaxiRide]{

  import TaxiRide._

  override def toString() : String = {
    val startTimes = isStart match {
      case true => ("START", startTime.toString(timeFormatter), endTime.toString(timeFormatter))
      case _ => ("END", endTime.toString(timeFormatter), startTime.toString(timeFormatter))
    }
    s"$rideId,${startTimes._1},${startTimes._2},${startTimes._3},$startLon,$startLat,$endLon,$endLat,$passengerCnt"
  }

  def getEventTime: Long = if (isStart) startTime.getMillis else endTime.getMillis

  override def compareTo(other: TaxiRide): Int = {
    other match {
      case value if(value == null) => 1
      case _ =>
        getEventTime.compareTo(other.getEventTime) match {
          case compareTimes if (compareTimes == 0)  =>
            if(isStart == other.isStart) 0
            else isStart match {
              case true => -1
              case _ => 1
            }
          case compareTimes => compareTimes
        }
    }
  }

  override def equals(other: Any): Boolean = other.isInstanceOf[TaxiRide] && this.rideId == other.asInstanceOf[TaxiRide].rideId

  override def hashCode: Int = this.rideId.toInt
}

object TaxiRide{

  val timeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.US).withZoneUTC

  def fromString(line: String) : TaxiRide = {
    val tokens = line.split(",")
    if (tokens.length != 9) throw new RuntimeException("Invalid record: " + line)
    try {
      val rideId = tokens(0).toLong
      val times = tokens(1) match {
        case "START" =>
          (true, DateTime.parse(tokens(2), timeFormatter), DateTime.parse(tokens(3), timeFormatter))
        case "END" =>
          (false, DateTime.parse(tokens(3), timeFormatter), DateTime.parse(tokens(2), timeFormatter))
        case _ =>
          throw new RuntimeException("Invalid record: " + line)
      }
      val startLon = if (tokens(4).length > 0) tokens(4).toFloat else .0f
      val startLat = if (tokens(5).length > 0) tokens(5).toFloat else .0f
      val endLon = if (tokens(6).length > 0) tokens(6).toFloat else .0f
      val endLat = if (tokens(7).length > 0) tokens(7).toFloat else .0f
      val passengerCnt = tokens(8).toShort
      new TaxiRide(rideId, times._1, times._2, times._3, startLon, startLat, endLon, endLat, passengerCnt)
    } catch {
      case nfe: NumberFormatException =>
        throw new RuntimeException(s"Invalid record: $line with error $nfe")
    }
  }
}