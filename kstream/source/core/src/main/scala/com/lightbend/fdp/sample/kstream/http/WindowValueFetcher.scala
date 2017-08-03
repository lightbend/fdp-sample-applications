package com.lightbend.fdp.sample.kstream
package http

import akka.http.scaladsl.model.StatusCodes._

import akka.stream.ActorMaterializer
import akka.actor.ActorSystem

import org.apache.kafka.streams.{ KafkaStreams }
import org.apache.kafka.streams.state.HostInfo
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.{ Future, ExecutionContext}
import scala.util.{ Success, Failure }

import com.typesafe.scalalogging.LazyLogging
import services.{ MetadataService, HostStoreInfo, LocalStateStoreQuery }
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import serializers.Serializers

class WindowValueFetcher(
  metadataService: MetadataService, 
  localStateStoreQuery: LocalStateStoreQuery[String, Long],
  httpRequester: HttpRequester, 
  streams: KafkaStreams, 
  executionContext: ExecutionContext, 
  hostInfo: HostInfo)(implicit actorSystem: ActorSystem) extends LazyLogging with FailFastCirceSupport with Serializers {

  private implicit val ec: ExecutionContext = executionContext

  def fetchWindowedAccessCountSummary(hostKey: String, fromTime: Long, toTime: Long): Future[List[(Long, Long)]] = 
    fetchWindowedSummary(hostKey, WeblogProcessing.WINDOWED_ACCESS_COUNT_PER_HOST_STORE, "/weblog/access/win/", fromTime, toTime) 

  def fetchWindowedPayloadSizeSummary(hostKey: String, fromTime: Long, toTime: Long): Future[List[(Long, Long)]] = 
    fetchWindowedSummary(hostKey, WeblogProcessing.WINDOWED_PAYLOAD_SIZE_PER_HOST_STORE, "/weblog/bytes/win/", fromTime, toTime) 

  private def fetchWindowedSummary(hostKey: String, store: String, path: String, 
    fromTime: Long, toTime: Long): Future[List[(Long, Long)]] = 

    metadataService.streamsMetadataForStoreAndKey(store, hostKey, stringSerializer) match {
      case Success(host) => {
        // hostKey is on another instance. call the other instance to fetch the data.
        if (!thisHost(host)) {
          logger.warn(s"Key $hostKey is on another instance not on $host - requerying ..")
          httpRequester.queryFromHost[List[(Long, Long)]](host, path)
        } else {
          // hostKey is on this instance
          localStateStoreQuery.queryWindowedStateStore(streams, store, hostKey, fromTime, toTime)
        }
      }
      case Failure(ex) => Future.failed(ex)
    }

  private def thisHost(host: HostStoreInfo): Boolean =
    host.host.equals(translateHostInterface(hostInfo.host)) && host.port == hostInfo.port
}


