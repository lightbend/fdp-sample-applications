package com.lightbend.ad.speculativemodelserver.actors

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.lightbend.ad.model.ModelWithDescriptor
import com.lightbend.model.cpudata.CPUData
import com.lightbend.speculative.speculativedescriptor.SpeculativeDescriptor


// Router actor, routing both model and data to an appropriate actors
// Based on http://michalplachta.com/2016/01/23/scalability-using-sharding-from-akka-cluster/

class ModelServingManager extends Actor {

  println(s"Creating Model Serving manager")

  implicit val askTimeout = Timeout(100, TimeUnit.MILLISECONDS)
  implicit val ec = context.dispatcher

  // Create support actors
  val modelManager = context.actorOf(ModelManager.props, "modelManager")
  val dataManager = context.actorOf(DataManager.props, "dataManager")

  override def receive = {
    // Model methods
    // Update model
    case model: ModelWithDescriptor => modelManager forward model
    // Get list of model servers
    case getModels : GetModels => modelManager forward getModels
    // Get state of the model
    case getState: GetModelServerState => modelManager forward getState

    // Data methods
    // Configure Data actor
    case configuration : SpeculativeDescriptor => dataManager forward SetSpeculativeServer(configuration.datatype, configuration.tmout)

    // process data
    case record: CPUData =>
//      println(s"model serving manager request $record from sender ${sender()}")
      val _ = ask(modelManager, GetModelActors(record.dataType)).mapTo[GetModelActorsResult]
        .map(actors => RecordWithModels(record, actors.models))
        .pipeTo(dataManager) (sender())
    // Get state of speculative executor
    case getState: GetSpeculativeServerState => dataManager forward getState
    // Get List of data processors
    case getProcessors : GetDataProcessors => dataManager forward getProcessors
  }
}

object ModelServingManager{
  def props : Props = Props(new ModelServingManager())
}
