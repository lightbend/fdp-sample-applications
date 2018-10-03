package com.lightbend.ad.speculativemodelserver.actors

import akka.actor.{Actor, ActorRef, Props}
import com.lightbend.ad.model.{ModelToServeStats, ModelWithDescriptor}
import com.lightbend.ad.speculativemodelserver.persistence.FilePersistence
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import scala.collection.mutable.Map


// Router actor, routing both model and data to an appropriate actor
// Based on http://michalplachta.com/2016/01/23/scalability-using-sharding-from-akka-cluster/

class ModelManager extends Actor {

  println(s"Creating Model manager")
  implicit val askTimeout = Timeout(5.seconds)

  // A map of actors per data type. Current limitation - can add models but not remove them
//  var actorsDataType = Map[String, Map[String, ActorRef]]()

  val savedModels = FilePersistence.getKnownModels()
  println(s"Restoring models $savedModels")

  savedModels.foreach(modelID =>
    FilePersistence.restoreModelState(modelID.substring("model_".length, modelID.length)) match {
      case Some(mwd) => self ? mwd
      case _ =>
    }
  )

  // Get (or create) model server actor based on model name
  private def getModelServer(dataType: String, modelID: String): ActorRef = {
    val modelname = s"${dataType}_$modelID"
    context.child(modelname).getOrElse(context.actorOf(ModelServingActor.props(modelname), modelname))
  }

  // Get instances
  private def getModels(dataType: String) : Seq[ActorRef] =
    context.children.toSeq.filter(_.path.name.startsWith(dataType))

  private def getModelNames(dataType: String) : GetModelsResult = GetModelsResult(getModels(dataType).map(_.path.name))

  override def receive = {
    // Redirect to model update. his only works for the local (in memory) invocation, because ModelWithDescriptor is not serializable
    case model: ModelWithDescriptor =>
       val modelServer = getModelServer(model.descriptor.dataType, model.descriptor.name)
      modelServer forward model
    // Get State of model server
    case getState: GetModelServerState => {
      context.child(getState.ModelID) match {
        case Some(actorRef) => actorRef forward getState
        case _ => sender() ! ModelToServeStats.empty
      }
    }
    // Get current list of existing models
    case getModels : GetModels =>
      sender() ! getModelNames(getModels.dataType)
    // Create actors from names. Support method for data processor configuration
    case getModelServersList : GetModelActors => sender() ! GetModelActorsResult(getModels(getModelServersList.dataType))
  }
}

object ModelManager{
  def props : Props = Props(new ModelManager())
}

case class GetModels(dataType: String)

case class GetModelsResult(models : Seq[String])

case class GetModelActors(dataType : String)

case class GetModelActorsResult(models : Seq[ActorRef])