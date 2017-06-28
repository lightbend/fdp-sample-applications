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


/**
 * A microservice that offers an http interface (through akka-http) to query Kafka Streams state store for
 * {@link WebLogProcessing}. 
 *
 * State stores in Kafka Streams are local to the instance. But in case of a distributed application where you
 * run multiple instances of the stream, states may be distributed across the cluster. In case of repartitioning,
 * states also migrate from one instance to another. So it's quite common to be in a situation when you query
 * for a specific state but find it has been migrated to other instances. 
 *
 * In Kafka Streams, metadata service provides the location of such state. This microservice allows users to
 * query for state and serves the information by transparantly collecting the relvant information from whichever
 * host / instance actually contains that information.
 *
 * <em>How to run this service</em>
 *
 * This service is started from {@link WebLogProcessing}, which is the main driver for the Kafka Streams example
 * application. Once it starts, the service can be used to query the state information. Assuming the service starts on 
 * host = localhost and port = 7070, here's how to query for the state:
 *
 * <pre>
 * {@code
 * # Get the number of times a hostname world.std.com has been accessed in the weblog
 * http://localhost:7070/weblog/access/world.std.com
 *
 * # Get the total bytes of payload transferred for hostname world.std.com as recorded in the weblog
 * http://localhost:7070/weblog/bytes/world.std.com
 * }
 * </pre>
 */

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
