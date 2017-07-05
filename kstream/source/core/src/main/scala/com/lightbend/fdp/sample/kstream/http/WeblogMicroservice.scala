package com.lightbend.fdp.sample.kstream
package http

import akka.actor.ActorSystem

import akka.http.scaladsl.server.Directives
import Directives._
import akka.http.scaladsl.Http

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.ExceptionHandler
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import akka.stream.ActorMaterializer

import io.circe.generic.auto._
import io.circe.syntax._

import org.apache.kafka.streams.{ KafkaStreams }
import org.apache.kafka.streams.state.HostInfo

import scala.concurrent.{ Future, ExecutionContext}
import scala.util.{ Try, Success, Failure }

import com.typesafe.scalalogging.LazyLogging
import services.{ MetadataService, HostStoreInfo, LocalStateStoreQuery }


class WeblogMicroservice(streams: KafkaStreams, hostInfo: HostInfo) 
  extends Directives with FailFastCirceSupport with LazyLogging {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  // service for fetching metadata information
  val metadataService = new MetadataService(streams)

  // service for fetching from local state store
  val localStateStoreQuery = new LocalStateStoreQuery[String, Long]

  // http service for request handling
  val httpRequester = new HttpRequester(system, materializer, executionContext)
  import httpRequester._

  // http layer for key/value store interface
  val keyValueFetcher = 
    new KeyValueFetcher(metadataService, localStateStoreQuery, httpRequester, 
      streams, executionContext, hostInfo)
  
  // http layer for windowing query from store interface
  val windowValueFetcher = 
    new WindowValueFetcher(metadataService, localStateStoreQuery, httpRequester, 
      streams, executionContext, hostInfo)

  // http layer for bloom filter based query from store interface
  val bfValueFetcher = 
    new BFValueFetcher(metadataService, localStateStoreQuery, httpRequester, 
      streams, executionContext, hostInfo)


  val myExceptionHandler = ExceptionHandler {
    case ex: Exception =>
      extractUri { uri =>
        logger.error(s"Request to $uri could not be handled normally", ex)
        complete(HttpResponse(InternalServerError, entity = "Request Failed!"))
      }
  }


  // define the routes
  val routes = handleExceptions(myExceptionHandler) {
    pathPrefix("weblog") {
      (get & pathPrefix("access" / "win") & path(Segment)) { hostKey =>
        complete {
          windowValueFetcher.fetchWindowedAccessCountSummary(hostKey, 0, System.currentTimeMillis).map(_.asJson)
        }
      } ~
      (get & pathPrefix("access" / "check") & path(Segment)) { hostKey =>
        complete {
          bfValueFetcher.checkIfPresent(hostKey).map(_.asJson)
        }
      } ~
      (get & pathPrefix("bytes" / "win") & path(Segment)) { hostKey =>
        complete {
          windowValueFetcher.fetchWindowedPayloadSizeSummary(hostKey, 0, System.currentTimeMillis).map(_.asJson)
        }
      } ~
      (get & pathPrefix("access") & path(Segment)) { hostKey =>
        complete {
          keyValueFetcher.fetchAccessCountSummary(hostKey).map(_.asJson)
        }
      } ~
      (get & pathPrefix("bytes") & path(Segment)) { hostKey =>
        complete {
          keyValueFetcher.fetchPayloadSizeSummary(hostKey).map(_.asJson)
        }
      }
    }
  }

  var bindingFuture: Future[Http.ServerBinding] = null


  // start the http server
  def start(): Unit = {
    bindingFuture = Http().bindAndHandle(routes, hostInfo.host, hostInfo.port)

    bindingFuture.onComplete {
      case Success(serverBinding) =>
        logger.info(s"Server bound to ${serverBinding.localAddress} ")

      case Failure(ex) =>
        logger.error(s"Failed to bind to ${hostInfo.host}:${hostInfo.port}!", ex)
        system.terminate()
    }
  }


  // stop the http server
  def stop(): Unit = {
    logger.info("Stopping the http server")
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
