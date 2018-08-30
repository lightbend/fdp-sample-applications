package com.lightbend.ad.speculativemodelserver.actors

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import com.lightbend.ad.model.ServingResult
import com.lightbend.ad.model.speculative.{ServingResponse, SpeculativeExecutionStats}
import com.lightbend.ad.speculativemodelserver.persistence.FilePersistence
import com.lightbend.ad.speculativemodelserver.processor.VotingDesider
import com.lightbend.speculative.speculativedescriptor.SpeculativeDescriptor

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

// Speculative model server manager for a given data type

class SpeculativeModelServingCollectorActor(dataType : String, tmout : Long) extends Actor {

  val SERVERTIMEOUT = 100l

  println(s"Creating speculative model serving collector actor $dataType")

  val decider = VotingDesider
  var timeout = new FiniteDuration(if(tmout > 0) tmout else  SERVERTIMEOUT, TimeUnit.MILLISECONDS)


  var state = SpeculativeExecutionStats(dataType, decider.getClass.getName, timeout.length)

  val currentProcessing = collection.mutable.Map[String, CurrentProcessing]()

  override def preStart : Unit = {
    timeout = FilePersistence.restoreDataState(dataType) match {
      case Some(tmout) => new FiniteDuration(if(tmout > 0) tmout else  SERVERTIMEOUT, TimeUnit.MILLISECONDS)
      case _ => new FiniteDuration(SERVERTIMEOUT, TimeUnit.MILLISECONDS)
    }
  }

  override def receive = {
    // Start speculative requesr
    case start : StartSpeculative =>
      // Set up the state
      implicit val ec = context.dispatcher
      currentProcessing += (start.GUID -> CurrentProcessing(start.models, start.start, start.reply, new ListBuffer[ServingResponse]())) // Add to watch list
      // Schedule timeout
      val _ = context.system.scheduler.scheduleOnce(timeout, self, start.GUID)
    // Result of indivirual model serving
    case servingResponse : ServingResponse =>
      currentProcessing.get(servingResponse.GUID) match {
      case Some(processingResults) =>
        // We are still waiting for this GUID
        val current = CurrentProcessing(processingResults.models, processingResults.start, processingResults.reply, processingResults.results += servingResponse)
        current.results.size match {
          case size if (size >= current.models) => processResult(servingResponse.GUID, current)  // We are done
          case _ => val _ = currentProcessing += (servingResponse.GUID -> current)               // Keep going
        }
      case _ => // Timed out
    }
    // Speculative execution completion
    case stop : String =>
      currentProcessing.contains(stop) match {
      case true => processResult(stop, currentProcessing(stop))
      case _ => // Its already done
    }
    // Current State request
    case request : GetSpeculativeServerState => sender() ! state
    // Configuration update
    case configuration : SpeculativeDescriptor =>
      timeout = new FiniteDuration(if(tmout > 0) tmout else  SERVERTIMEOUT, TimeUnit.MILLISECONDS)
      state = state.updateConfig(tmout)
      FilePersistence.saveDataState(dataType, configuration.tmout)
      sender() ! "Done"
  }

  // Complete speculative execution
  private def processResult(GUID : String, results: CurrentProcessing) : Unit = {
    // Run it through decider
    val servingResult = decider.decideResult(results.results.toList).asInstanceOf[ServingResult]
    // Send the reply
    results.reply ! servingResult
    // Update state
    if(servingResult.processed)
      state = state.incrementUsage(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - results.start))
    // remove state
    val _ = currentProcessing -= GUID
  }
}

object SpeculativeModelServingCollectorActor{
  def props(dataType : String, tmout : Long) : Props = Props(new SpeculativeModelServingCollectorActor(dataType, tmout))
}

case class StartSpeculative(GUID : String, start : Long, reply: ActorRef, models : Int)

case class CurrentProcessing(models : Int, start : Long, reply: ActorRef, results : ListBuffer[ServingResponse])

case class GetSpeculativeServerState(dataType : String)