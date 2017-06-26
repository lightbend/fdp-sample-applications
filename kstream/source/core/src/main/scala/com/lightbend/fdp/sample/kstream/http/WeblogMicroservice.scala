package com.lightbend.fdp.sample.kstream
package http

import akka.actor.ActorSystem

import akka.http.scaladsl.server.Directives
import Directives._
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding

import akka.http.scaladsl.model.{HttpResponse, HttpRequest, HttpMethods}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.StatusCodes.{ Success => HttpSuccess }
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.unmarshalling.Unmarshal
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import io.circe.generic.auto._
import io.circe.syntax._

import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.state.{ HostInfo, ReadOnlyKeyValueStore, QueryableStoreTypes, QueryableStoreType }

import java.io.IOException

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{ Try, Success, Failure }

import com.typesafe.scalalogging.LazyLogging
import services.{ MetadataService, HostStoreInfo }


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

  val metadataService = new MetadataService(streams)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  

  private def fetchAccessCountSummary(hostName: String): Future[Either[String, Long]] = 
    fetchSummaryInfo(WeblogProcessing.ACCESS_COUNT_PER_HOST_STORE, "/weblog/access/" + hostName, hostName)


  private def fetchPayloadSizeSummary(hostName: String): Future[Either[String, Long]] =
    fetchSummaryInfo(WeblogProcessing.PAYLOAD_SIZE_PER_HOST_STORE, "/weblog/bytes/" + hostName, hostName)


  private def fetchSummaryInfo(store: String, path: String, hostName: String): Future[Either[String, Long]] = {

    // The hostName might be hosted on another instance. We need to find which instance it is on
    // and then perform a remote lookup if necessary.
    val host: HostStoreInfo =
      metadataService.streamsMetadataForStoreAndKey(
        store, 
        hostName, 
        new StringSerializer())

    // hostName is on another instance. call the other instance to fetch the data.
    if (!thisHost(host)) {
      logger.warn(s"Key $hostName is on another instance not on $host - requerying ..")
      queryFromHost(host, path)
    } else {
      // hostName is on this instance
      queryStateStore(streams, store, hostName)
    }
  }


  private def apiConnectionFlow(host: String, port: Int): Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(host, port)


  private def apiRequest(request: HttpRequest, host: HostStoreInfo): Future[HttpResponse] = {
    logger.debug(request.toString)
    Source.single(request).via(apiConnectionFlow(host.host, host.port)).runWith(Sink.head)
  }


  private def queryFromHost(host: HostStoreInfo, 
    path: String): Future[Either[String, Long]] = {
    logger.debug(s"Path to query $path")
    apiRequest(RequestBuilding.Get(path), host).flatMap { response =>
      response.status match {
        case OK         => Unmarshal(response.entity).to[Either[String, Long]]
         
        case BadRequest => {
          logger.error(s"$path: incorrect path")
          Future.successful(Left(s"$path: incorrect path"))
        }

        case _          => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"state fetch request failed with status code ${response.status} and entity $entity"
          logger.error(error)
          Future.failed(new IOException(error))
        }
      }
    }
  }


  // query host specific information from the state store
  private def queryStateStore(streams: KafkaStreams, store: String, host: String): Future[Either[String, Long]] = Future {
    Try {
      val q: QueryableStoreType[ReadOnlyKeyValueStore[String, Long]] = QueryableStoreTypes.keyValueStore()
      val localStore: ReadOnlyKeyValueStore[String, Long] = streams.store(store, q)
      localStore.get(host)
    } match {
      case Success(s)  => Right(s)
      case Failure(ex) => Left(ex.getMessage)
    }
  }


  private def thisHost(host: HostStoreInfo): Boolean =
    host.host.equals(hostInfo.host) && host.port == hostInfo.port

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
      (get & pathPrefix("access") & path(Segment)) { hostKey =>
        complete {
          fetchAccessCountSummary(hostKey).map(_.asJson)
        }
      } ~
      (get & pathPrefix("bytes") & path(Segment)) { hostKey =>
        complete {
          fetchPayloadSizeSummary(hostKey).map(_.asJson)
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
