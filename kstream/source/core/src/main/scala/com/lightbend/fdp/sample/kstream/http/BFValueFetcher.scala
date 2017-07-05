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
import processor.WeblogDriver

class BFValueFetcher(
  metadataService: MetadataService, 
  localStateStoreQuery: LocalStateStoreQuery[String, Long],
  httpRequester: HttpRequester, 
  streams: KafkaStreams, 
  executionContext: ExecutionContext, 
  hostInfo: HostInfo) extends LazyLogging with FailFastCirceSupport with Serializers {

  private implicit val ec: ExecutionContext = executionContext

  def checkIfPresent(hostKey: String): Future[Boolean] = {

    val store = WeblogDriver.LOG_COUNT_STATE_STORE
    val path = "/weblog/access/check/"

    metadataService.streamsMetadataForStoreAndKey(store, hostKey, stringSerializer) match {
      case Success(host) => {
        // hostKey is on another instance. call the other instance to fetch the data.
        if (!thisHost(host)) {
          logger.warn(s"Key $hostKey is on another instance not on $host - requerying ..")
          httpRequester.queryFromHost[Boolean](host, path)
        } else {
          // hostKey is on this instance
          localStateStoreQuery.queryBFStore(streams, store, hostKey)
        }
      }
      case Failure(ex) => Future.failed(ex)
    }
  }

  private def thisHost(host: HostStoreInfo): Boolean =
    host.host.equals(hostInfo.host) && host.port == hostInfo.port
}


