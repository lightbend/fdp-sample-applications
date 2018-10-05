package com.lightbend.ad.modelserver.actors

import akka.actor.{Actor, ActorRef, Props}
import com.lightbend.ad.model.{ModelToServeStats, ModelWithDescriptor}
import com.lightbend.model.cpudata.CPUData


// Router actor, routing both model and data to an appropriate actor
// Based on http://michalplachta.com/2016/01/23/scalability-using-sharding-from-akka-cluster/

class ModelServingManager extends Actor {

  private def getModelServer(dataType: String): ActorRef = {
    context.child(dataType).getOrElse(context.actorOf(ModelServingActor.props(dataType), dataType))
  }

  private def getInstances : GetModelsResult =
    GetModelsResult(context.children.map(_.path.name).toSeq)

  override def receive = {
    case model: ModelWithDescriptor => getModelServer(model.descriptor.dataType) forward model

    case record: CPUData => {
      val dataType = record.dataType match{
        case "" => "cpu"
        case _ => record.dataType
      }
      getModelServer(dataType) forward record
    }

    case getState: GetState => context.child(getState.dataType) match{
      case Some(server) => server forward getState
      case _ => sender() ! ModelToServeStats.empty
    }

    case getModels : GetModels => sender() ! getInstances
  }
}

object ModelServingManager{
  def props : Props = Props(new ModelServingManager())
}

case class GetModels()

case class GetModelsResult(models : Seq[String])
