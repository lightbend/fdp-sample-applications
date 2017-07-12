package com.lightbend.fdp.sample.kstream
package http

import akka.actor.ActorSystem

import akka.stream.ActorMaterializer

import io.circe.generic.auto._
import io.circe.syntax._

import org.apache.kafka.streams.state.HostInfo

import scala.concurrent.ExecutionContext


class WeblogProcHttpService(
  hostInfo: HostInfo, 
  bfValueFetcher: BFValueFetcher,
  actorSystem: ActorSystem,
  actorMaterializer: ActorMaterializer,
  ec: ExecutionContext
) extends InteractiveQueryHttpService(hostInfo, actorSystem, actorMaterializer, ec) { 


  // define the routes
  val routes = handleExceptions(myExceptionHandler) {
    pathPrefix("weblog") {
      (get & pathPrefix("access" / "check") & path(Segment)) { hostKey =>
        complete {
          bfValueFetcher.checkIfPresent(hostKey).map(_.asJson)
        }
      }
    }
  }
}

