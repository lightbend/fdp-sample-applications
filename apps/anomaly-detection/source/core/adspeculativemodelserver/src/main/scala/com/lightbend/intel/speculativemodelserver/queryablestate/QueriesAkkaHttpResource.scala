package com.lightbend.ad.speculativemodelserver.queryablestate

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.lightbend.ad.model.ModelToServeStats
import com.lightbend.ad.model.speculative.SpeculativeExecutionStats
import com.lightbend.ad.speculativemodelserver.actors._
import de.heikoseeberger.akkahttpjackson.JacksonSupport

import scala.concurrent.duration._

object QueriesAkkaHttpResource extends JacksonSupport {

  implicit val askTimeout = Timeout(30.seconds)

  def storeRoutes(modelserver: ActorRef): Route =
    get {
      // Get statistics for a given model
      path("model"/Segment) { modelID =>
        onSuccess(modelserver ? GetModelServerState(modelID)) {
          case info: ModelToServeStats =>
            complete(info)
        }
      } ~
      // Get list of models for a given data type
      path("models"/Segment) { dataType =>
        onSuccess(modelserver ? GetModels(dataType)) {
          case models: GetModelsResult =>
            complete(models)
        }
      } ~
      // Get statistics for a given data type
      path("processor"/Segment) { dataType =>
        onSuccess(modelserver ? GetSpeculativeServerState(dataType)) {
          case info: SpeculativeExecutionStats =>
            complete(info)
        }
      }~
      // Get list of data types
      path("processors") {
        onSuccess(modelserver ? GetDataProcessors()) {
          case processors: GetDataProcessorsResult =>
            complete(processors)
        }
      }
    }
}