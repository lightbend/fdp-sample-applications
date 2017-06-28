package com.lightbend.fdp.sample.kstream
package http

import akka.stream.ActorMaterializer

import org.apache.kafka.streams.{ KafkaStreams }
import org.apache.kafka.streams.state.HostInfo
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.{ Future, ExecutionContext}
import scala.util.{ Success, Failure }

import com.typesafe.scalalogging.LazyLogging
import services.{ MetadataService, HostStoreInfo, LocalStateStoreQuery }
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import serializers.Serializers

class KeyValueFetcher(
  metadataService: MetadataService, 
  localStateStoreQuery: LocalStateStoreQuery[String, Long],
  httpRequester: HttpRequester, 
  streams: KafkaStreams, 
  executionContext: ExecutionContext, 
  hostInfo: HostInfo) extends LazyLogging with FailFastCirceSupport with Serializers {

  private implicit val ec: ExecutionContext = executionContext

  def fetchAccessCountSummary(hostKey: String): Future[Long] = 
    fetchSummaryInfo(WeblogProcessing.ACCESS_COUNT_PER_HOST_STORE, "/weblog/access/" + hostKey, hostKey)

  def fetchPayloadSizeSummary(hostKey: String): Future[Long] =
    fetchSummaryInfo(WeblogProcessing.PAYLOAD_SIZE_PER_HOST_STORE, "/weblog/bytes/" + hostKey, hostKey)

  private def fetchSummaryInfo(store: String, path: String, hostKey: String): Future[Long] = 

    metadataService.streamsMetadataForStoreAndKey(store, hostKey, stringSerializer) match {
      case Success(host) => {
        // hostKey is on another instance. call the other instance to fetch the data.
        if (!thisHost(host)) {
          logger.warn(s"Key $hostKey is on another instance not on $host - requerying ..")
          httpRequester.queryFromHost[Long](host, path)
        } else {
          // hostKey is on this instance
          localStateStoreQuery.queryStateStore(streams, store, hostKey)
        }
      }
      case Failure(ex) => Future.failed(ex)
    }

  private def thisHost(host: HostStoreInfo): Boolean =
    host.host.equals(hostInfo.host) && host.port == hostInfo.port
}

