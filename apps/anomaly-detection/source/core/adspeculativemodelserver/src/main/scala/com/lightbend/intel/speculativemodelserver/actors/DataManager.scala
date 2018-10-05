package com.lightbend.ad.speculativemodelserver.actors

import akka.actor.{Actor, ActorRef, Props}
import com.lightbend.ad.model.speculative.SpeculativeExecutionStats
import com.lightbend.ad.speculativemodelserver.persistence.FilePersistence
import com.lightbend.model.cpudata.CPUData
import com.lightbend.speculative.speculativedescriptor.SpeculativeDescriptor


// Router actor, routing both model and data to an appropriate actor
// Based on http://michalplachta.com/2016/01/23/scalability-using-sharding-from-akka-cluster/

class DataManager extends Actor {

  println("Creating Data Manager")

  private val STARTERPREFIX = "starter"
  private val COLLECTORPREFIX = "collector"

  private def starterActorName(dataType: String) : String = s"$STARTERPREFIX$dataType"
  private def collectorActorName(dataType: String) : String = s"$COLLECTORPREFIX$dataType"

  private def starterDataType(name: String) : String = name.replace(STARTERPREFIX, "")

  private def getDataServerStarter(dataType: String): Option[ActorRef] = context.child(starterActorName(dataType))

  private def getDataServerCollector(dataType: String): Option[ActorRef] = context.child(collectorActorName(dataType))

  private def createDataServers(dataType: String, tmout : Long) : ActorRef = {
    FilePersistence.saveDataState(dataType,tmout)
    val collector = context.actorOf(SpeculativeModelServingCollectorActor.props(dataType,tmout), collectorActorName(dataType))
    context.actorOf(SpeculativeModelServingStarterActor.props(dataType, collector), starterActorName(dataType))
  }

  private def getInstances : GetDataProcessorsResult =
    GetDataProcessorsResult(context.children.map(_.path.name).filter(_.startsWith(STARTERPREFIX)).map(starterDataType(_)).toSeq)


  override def receive = {

    // Configuration update
    case configuration : SetSpeculativeServer =>
      getDataServerStarter(configuration.datatype) match {
        case Some(starter) => // Update existing
          getDataServerCollector(configuration.datatype).get forward
            SpeculativeDescriptor(configuration.datatype, configuration.tmout)
        case _ => // Create the new ones
          createDataServers(configuration.datatype, configuration.tmout)
          sender() ! "Done"
      }
    // process data record
    case record: RecordWithModels =>
      getDataServerStarter(record.record.dataType) match {
      case Some(actor) => actor forward record
      case _ => createDataServers(record.record.dataType, -1) forward record
    }
    // Get current state
    case getState: GetSpeculativeServerState => {
      getDataServerCollector(getState.dataType) match {
        case Some(collector) => collector forward getState
        case _ => sender() ! SpeculativeExecutionStats.empty
      }
    }
    // Get List of data processors
    case getProcessors : GetDataProcessors => sender() ! getInstances
  }
}

object DataManager{
  def props : Props = Props(new DataManager())
}

case class GetDataProcessors()

case class GetDataProcessorsResult(processors : Seq[String])

case class SetSpeculativeServer(datatype : String, tmout : Long)