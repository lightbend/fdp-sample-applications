package com.lightbend.ad.speculativemodelserver.actors

import java.util.concurrent.{ThreadLocalRandom, TimeUnit}

import akka.actor.{Actor, Props}
import com.lightbend.ad.influx.{InfluxDBSink, ServingModelData}
import com.lightbend.ad.model.speculative.{ServingRequest, ServingResponse}
import com.lightbend.ad.model.{Model, ModelToServeStats, ModelWithDescriptor, ServingResult}
import com.lightbend.ad.speculativemodelserver.persistence.FilePersistence
import com.lightbend.model.cpudata.CPUData


// Workhorse - doing model serving for a given data type/ model

class ModelServingActor(modelID : String) extends Actor {

  println(s"Creating model serving actor $modelID")
  private var currentModel: Option[Model] = None
  private var newModel: Option[Model] = None
  private var currentState: Option[ModelToServeStats] = None
  private var newState: Option[ModelToServeStats] = None

  val influxDBSink = InfluxDBSink()

  override def receive = {

    // Update Model. This only works for the local (in memory) invocation, because ModelWithDescriptor is not serializable
    case model : ModelWithDescriptor =>
      // Update model
      println(s"Model Server $modelID has a new model $model")
      newState = Some(ModelToServeStats(model.descriptor))
      newModel = Some(model.model)
      FilePersistence.saveModelState(modelID, model)
      sender() ! "Done"
    // Process data
    case record : ServingRequest =>
      // Process data
      newModel.foreach { model =>
        // Update model
        // close current model first
        currentModel.foreach(_.cleanup())
        // Update model
        currentModel = newModel
        currentState = newState
        newModel = None
      }

      currentModel match {
        case Some(model) =>
          val start = System.nanoTime()
          val result = model.score(record.data.asInstanceOf[CPUData])
          val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
          currentState = currentState.map(_.incrementUsage(duration))
          sender() ! ServingResponse(record.GUID,ServingResult(currentState.get.description, record.data.asInstanceOf[CPUData]._class, result, duration))
          result match {
            case Some(data) =>
              val _ =influxDBSink.write(new ServingModelData(data.toLong, currentState.get.description, duration))
            case _ =>
          }
        case _ =>
          sender() ! ServingResult.noModel
      }
    // Get current state
    case request : GetModelServerState => {
      sender() ! currentState.getOrElse(ModelToServeStats.empty)
    }
  }
}

object ModelServingActor{
  def props(modelID : String) : Props = Props(new ModelServingActor(modelID))
}

case class GetModelServerState(ModelID : String)
