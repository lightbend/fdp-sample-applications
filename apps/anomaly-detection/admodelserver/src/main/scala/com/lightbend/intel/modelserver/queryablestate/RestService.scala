package com.lightbend.ad.modelserver.queryablestate

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{complete, get, onSuccess, path}
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.lightbend.ad.modelserver.actors.{GetModels, GetModelsResult, GetState}
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import com.lightbend.ad.configuration.IntelConfig
import com.lightbend.ad.model.ModelToServeStats
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object RestService {

  // Create context
  val settings = IntelConfig.fromConfig(ConfigFactory.load()).get
  import settings._


  // See http://localhost:5500/models
  // Then select a model shown and try http://localhost:5500/state/<model>, e.g., http://localhost:5500/state/wine
  def startRest(modelserver: ActorRef)(implicit system: ActorSystem, materializer: ActorMaterializer): Unit = {

    implicit val executionContext = system.dispatcher
    implicit val timeout = Timeout(10.seconds)
    val host = "0.0.0.0"
    val port = modelServerConfig.port
    val routes: Route = QueriesAkkaHttpResource.storeRoutes(modelserver)

    val _ = Http().bindAndHandle(routes, host, port) map
      { binding => println(s"Starting models observer on port ${binding.localAddress}") } recover {
      case ex =>
        println(s"Models observer could not bind to $host:$port - ${ex.getMessage}")
    }
  }
}

object QueriesAkkaHttpResource extends JacksonSupport {

  implicit val askTimeout = Timeout(30.seconds)

  def storeRoutes(modelserver: ActorRef): Route =
    get {
      path("state"/Segment) { datatype =>
        onSuccess(modelserver ? GetState(datatype)) {
          case info: ModelToServeStats =>
            complete(info)
        }
      } ~
        path("models") {
          onSuccess(modelserver ? GetModels()) {
            case models: GetModelsResult =>
              complete(models)
          }
        }
    }
}
