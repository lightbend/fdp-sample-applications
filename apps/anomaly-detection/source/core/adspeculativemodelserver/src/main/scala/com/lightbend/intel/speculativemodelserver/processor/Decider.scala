package com.lightbend.intel.speculativemodelserver.processor

import akka.actor.ActorRef
import com.lightbend.ad.model.speculative.ServingResponse

import scala.collection.mutable.ListBuffer

trait Decider {

  def decideResult(results: CurrentProcessingResults): Any
}

case class CurrentProcessingResults(models : Int, start : Long, reply: ActorRef, results : ListBuffer[ServingResponse])