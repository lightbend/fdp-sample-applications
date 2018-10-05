package com.lightbend.ad.speculativemodelserver.actors

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import akka.util.Timeout
import com.lightbend.ad.model.ServingResult
import com.lightbend.ad.model.speculative.ServingRequest
import com.lightbend.model.cpudata.CPUData

// Speculative model server manager for a given data type

class SpeculativeModelServingStarterActor(dataType : String, collector : ActorRef) extends Actor {

  implicit val askTimeout = Timeout(100, TimeUnit.MILLISECONDS)

  println(s"Creating speculative model serving starter actor $dataType and collector $collector")

  override def receive = {
    // Model serving request
    case record : RecordWithModels =>
//      println(s"Speculative starter request $record from sender ${sender()}")
      record.models.size match {
        case size if size > 0 =>
          val request = ServingRequest (UUID.randomUUID ().toString, record.record)
          collector ! StartSpeculative (request.GUID, System.nanoTime (), sender (), record.models.size)
          record.models.foreach (_ tell (request, collector) )
        case _ => sender() ! ServingResult.noModel
      }
  }
}

object SpeculativeModelServingStarterActor{
  def props(dataType : String, collector : ActorRef) : Props = Props(new SpeculativeModelServingStarterActor(dataType, collector))
}

case class RecordWithModels(record : CPUData, models : Seq[ActorRef])
