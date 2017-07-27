package com.lightbend.fdp.sample.kstream

import java.util.Properties
import java.util.concurrent.TimeUnit

import java.util.concurrent.Executors
import scala.collection.JavaConverters._
import scala.util.{ Success, Failure }
import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import org.apache.kafka.streams.processor.{ StateStoreSupplier, TopologyBuilder }
import org.apache.kafka.streams.state.{ Stores, HostInfo }
import org.apache.kafka.streams.{ StreamsConfig, KafkaStreams }
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.clients.consumer.ConsumerConfig;

import serializers.Serializers
import config.KStreamConfig._
import http.{ WeblogProcHttpService, HttpRequester, BFValueFetcher }
import services.{ MetadataService, LocalStateStoreQuery }
import processor.{ BFStoreSupplier, WeblogProcessor }

object WeblogDriver extends WeblogWorkflow {

  final val LOG_COUNT_STATE_STORE = "log-counts"

  def main(args: Array[String]): Unit = workflow()

  override def startRestProxy(streams: KafkaStreams, hostInfo: HostInfo,
    actorSystem: ActorSystem, materializer: ActorMaterializer): WeblogProcHttpService = {

    implicit val system = actorSystem

    lazy val defaultParallelism: Int = {
      val rt = Runtime.getRuntime()
      rt.availableProcessors() * 4
    }

    def defaultExecutionContext(parallelism: Int = defaultParallelism): ExecutionContext = 
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(parallelism))
    
    val executionContext = defaultExecutionContext()

    // service for fetching metadata information
    val metadataService = new MetadataService(streams)
  
    // service for fetching from local state store
    val localStateStoreQuery = new LocalStateStoreQuery[String, Long]
  
    // http service for request handling
    val httpRequester = new HttpRequester(system, materializer, executionContext)
  
    val restService = new WeblogProcHttpService(
      hostInfo, 
      new BFValueFetcher(metadataService, localStateStoreQuery, httpRequester, streams, executionContext, hostInfo),
      system, materializer, executionContext
    )
    restService.start()
    restService
  }
  
  override def createStreams(config: ConfigData): KafkaStreams = {
    val changelogConfig = {
      val cfg = new java.util.HashMap[String, String]
      val segmentSizeBytes = (20 * 1024 * 1024).toString
      cfg.put("segment.bytes", segmentSizeBytes)
      cfg
    }

    // Kafka stream configuration
    val streamingConfig = {
      val settings = new Properties
      settings.put(StreamsConfig.APPLICATION_ID_CONFIG, "kstream-log-count")
      settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, config.brokers)
      settings.put(StreamsConfig.KEY_SERDE_CLASS_CONFIG, Serdes.String.getClass.getName)
      settings.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, Serdes.String.getClass.getName)

      // setting offset reset to earliest so that we can re-run the demo code with the same pre-loaded data
      // Note: To re-run the demo, you need to use the offset reset tool:
      // https://cwiki.apache.org/confluence/display/KAFKA/Kafka+Streams+Application+Reset+Tool
      settings.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

      // need this for query service
      val endpointHostName = translateHostInterface(config.httpInterface)
      logger.info(s"Endpoint host name $endpointHostName")

      settings.put(StreamsConfig.APPLICATION_SERVER_CONFIG, s"$endpointHostName:${config.httpPort}")

      // default is /tmp/kafka-streams
      settings.put(StreamsConfig.STATE_DIR_CONFIG, config.stateStoreDir)

      // Set the commit interval to 500ms so that any changes are flushed frequently and the summary
      // data are updated with low latency.
      settings.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "500");

      settings
    }

    val builder: TopologyBuilder = new TopologyBuilder()
    builder.addSource("Source", config.fromTopic)
           .addProcessor("Process", WeblogProcessorSupplier, "Source")
           .addStateStore(new BFStoreSupplier[String](LOG_COUNT_STATE_STORE, stringSerde, true, changelogConfig), "Process")

    new KafkaStreams(builder, streamingConfig)
  }
}

import org.apache.kafka.streams.processor.ProcessorSupplier
object WeblogProcessorSupplier extends ProcessorSupplier[String, String] {
  override def get(): WeblogProcessor = new WeblogProcessor()
}
