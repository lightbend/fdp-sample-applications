package com.lightbend.fdp.sample.kstream

import java.io.{PrintWriter, StringWriter}
import java.lang.{Long => JLong}
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.lightbend.fdp.sample.kstream.config.KStreamConfig._
import com.lightbend.fdp.sample.kstream.http.{HttpRequester, KeyValueFetcher, WeblogDSLHttpService, WindowValueFetcher}
import com.lightbend.fdp.sample.kstream.models.{LogParseUtil, LogRecord}
import com.lightbend.fdp.sample.kstream.services.{LocalStateStoreQuery, MetadataService}
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.kstream._
import org.apache.kafka.streams.state.HostInfo
import org.apache.kafka.streams.{KafkaStreams, KeyValue, StreamsConfig}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object WeblogProcessing extends WeblogWorkflow {

  final val ACCESS_COUNT_PER_HOST_STORE = "access-count-per-host"
  final val PAYLOAD_SIZE_PER_HOST_STORE = "payload-size-per-host"
  final val WINDOWED_ACCESS_COUNT_PER_HOST_STORE = "windowed-access-count-per-host"
  final val WINDOWED_PAYLOAD_SIZE_PER_HOST_STORE = "windowed-payload-size-per-host"

  def main(args: Array[String]): Unit = workflow()

  override def startRestProxy(streams: KafkaStreams, hostInfo: HostInfo,
                              actorSystem: ActorSystem, materializer: ActorMaterializer): WeblogDSLHttpService = {

    implicit val system = actorSystem

    lazy val defaultParallelism: Int = {
      val rt = Runtime.getRuntime
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

    val restService = new WeblogDSLHttpService(
      hostInfo,
      new KeyValueFetcher(metadataService, localStateStoreQuery, httpRequester, streams, executionContext, hostInfo),
      new WindowValueFetcher(metadataService, localStateStoreQuery, httpRequester, streams, executionContext, hostInfo),
      system, materializer, executionContext
    )
    restService.start()
    restService
  }

  override def createStreams(config: ConfigData): KafkaStreams = {
    // Kafka stream configuration
    val streamingConfig = {
      val settings = new Properties
      settings.put(StreamsConfig.APPLICATION_ID_CONFIG, "kstream-weblog-processing")
      settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, config.brokers)

      config.schemaRegistryUrl.foreach{ url =>
        settings.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, url)
      }

      settings.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.ByteArray.getClass.getName)
      settings.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String.getClass.getName)

      // setting offset reset to earliest so that we can re-run the demo code with the same pre-loaded data
      // Note: To re-run the demo, you need to use the offset reset tool:
      // https://cwiki.apache.org/confluence/display/KAFKA/Kafka+Streams+Application+Reset+Tool
      settings.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

      // need this for query service
      val endpointHostName = translateHostInterface(config.httpInterface)
      logger.info(s"Endpoint host name $endpointHostName")

      settings.put(StreamsConfig.APPLICATION_SERVER_CONFIG, s"$endpointHostName:${config.httpPort}")

      // default is /tmp/kafka-streams
      settings.put(StreamsConfig.STATE_DIR_CONFIG, config.stateStoreDir)

      // Set the commit interval to 500ms so that any changes are flushed frequently and the summary
      // data are updated with low latency.
      settings.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "500")

      settings
    }

    val builder = new KStreamBuilder()

    generateLogRecords(builder, config)

    //
    // assumption : the topic contains serialized records of LogRecord (serialized through logRecordSerde)
    val logRecords: KStream[Array[Byte], LogRecord] = builder.stream(byteArraySerde, logRecordSerde, config.toTopic.get)

    generateAvro(logRecords, builder, config)
    hostCountSummary(logRecords, builder, config)
    totalPayloadPerHostSummary(logRecords, builder, config)

    new KafkaStreams(builder, streamingConfig)
  }

  /**
    * Clean and format input data.  Redirect records that cause a parsing error to the error topic.
    */
  def generateLogRecords(builder: KStreamBuilder, config: ConfigData): Unit = {

    // will read network data from `fromTopic`
    val logs: KStream[Array[Byte], String] = builder.stream(config.fromTopic)

    // extract values after transformation
    val extracted: KStream[Array[Byte], Extracted] = logs.mapValues { record =>
      LogParseUtil.parseLine(record) match {
        case Success(r) => ValidLogRecord(r)
        case Failure(ex) => ValueError(ex, record)
      }
    }

    val predicateValid = new Predicate[Array[Byte], Extracted] {
      def test(key: Array[Byte], value: Extracted): Boolean = value match {
        case ValidLogRecord(_) => true
        case _ => false
      }
    }

    val predicateError = new Predicate[Array[Byte], Extracted] {
      def test(key: Array[Byte], value: Extracted): Boolean = value match {
        case ValueError(_, _) => true
        case _ => false
      }
    }

    // fan out stream to create output streams for labelled data and errors
    val filtered: Array[KStream[Array[Byte], Extracted]] = extracted.branch(predicateValid, predicateError)

    // push the labelled data
    val validated: KStream[Array[Byte], LogRecord] = filtered(0).mapValues {
      case ValidLogRecord(r) => r
      case _ => ??? // should never happen since we pre-emptively filtered with `branch`
    }

    validated.to(byteArraySerde, logRecordSerde, config.toTopic.get)

    // push the extraction errors
    val errors: KStream[Array[Byte], (String, String)] = filtered(1).mapValues {
      case ValueError(e, v) =>
        val writer = new StringWriter()
        e.printStackTrace(new PrintWriter(writer))
        (writer.toString, v)
      case _ => ??? // should never happen since we pre-emptively filtered with `branch`
    }

    errors.to(byteArraySerde, tuple2StringSerde, config.errorTopic)
  }

  sealed abstract class Extracted { }
  final case class ValidLogRecord(record: LogRecord) extends Extracted
  final case class ValueError(exception: Throwable, originalRecord: String) extends Extracted

  def generateAvro(logRecords: KStream[Array[Byte], LogRecord], builder: KStreamBuilder, config: ConfigData): Unit = {
    val records: KStream[Array[Byte], LogRecordAvro] = logRecords.mapValues(makeAvro)
    records.to(byteArraySerde, logRecordAvroSerde(config.schemaRegistryUrl), config.avroTopic.get)
  }

  /**
    * Transform a LogRecord into an Avro SpecificRecord, LogRecordAvro, generated by the Avro compiler
    */
  def makeAvro(record: LogRecord): LogRecordAvro =
    LogRecordAvro.newBuilder()
      .setHost(record.host)
      .setClientId(record.clientId)
      .setUser(record.user)
      .setTimestamp(record.timestamp.format(DateTimeFormatter.ofPattern("yyyy MM dd")))
      .setMethod(record.method)
      .setEndpoint(record.endpoint)
      .setProtocol(record.protocol)
      .setHttpReplyCode(record.httpReplyCode)
      .setPayloadSize(record.payloadSize)
      .build()

  /**
    * Summary count of number of times each host has been accessed
    */
  def hostCountSummary(logRecords: KStream[Array[Byte], LogRecord], builder: KStreamBuilder, config: ConfigData): Unit = {
    // we want to compute the number of times each host is accessed, hence get the host name
    val hosts: KStream[Array[Byte], String] = logRecords.mapValues(record => record.host)

    // we are changing the key here so that we can do a groupByKey later
    val hostPairs: KStream[String, String] = hosts.map ((_, value) => new KeyValue(value, value))

    // keys have changed - hence need new serdes
    val groupedStream: KGroupedStream[String, String] =
      hostPairs.groupByKey(stringSerde, stringSerde)

    // since this is a KTable (changelog stream), only the latest summarized information
    // for a host will be the correct one - all earlier records will be considered out of date
    val counts: KTable[String, java.lang.Long] = groupedStream.count(ACCESS_COUNT_PER_HOST_STORE)

    val windowedCounts: KTable[Windowed[String], java.lang.Long] =
      groupedStream.count(TimeWindows.of(60000), WINDOWED_ACCESS_COUNT_PER_HOST_STORE)

    // materialize the summarized information into a topic
    counts.to(stringSerde, longSerde, config.summaryAccessTopic.get)
    windowedCounts.to(windowedSerde, longSerde, config.windowedSummaryAccessTopic.get)

    // print the topic info (for debugging)
    builder.stream(stringSerde, longSerde, config.summaryAccessTopic.get).print()
    // builder.stream(windowedSerde, longSerde, config.windowedSummaryAccessTopic.get).print()
  }

  /**
    * Aggregate value of payloadSize per host
    */
  def totalPayloadPerHostSummary(logRecords: KStream[Array[Byte], LogRecord], builder: KStreamBuilder, config: ConfigData): Unit = {
    // extract the hostname and payload size from the record
    val payloads: KStream[Array[Byte], (String, JLong)] =
      logRecords.mapValues(record => (record.host, record.payloadSize))

    // we are changing the key here so that we can do a groupByKey later
    val n: KStream[String, JLong] = payloads.map {
      case (_, (host, size)) => new KeyValue(host, size)
    }

    val groupedStream: KGroupedStream[String, JLong] = n.groupByKey(stringSerde, longSerde)

    val payloadSize: KTable[String, JLong] = groupedStream
      .aggregate(
        (() => 0L): Initializer[JLong],
        ((_, s: JLong, agg: JLong) => s + agg): Aggregator[String, JLong, JLong],
        longSerde,
        PAYLOAD_SIZE_PER_HOST_STORE
      )

    val windowedPayloadSize: KTable[Windowed[String], JLong] = groupedStream
      .aggregate(
        (() => 0L): Initializer[JLong],
        ((_, s: JLong, agg: JLong) => s + agg): Aggregator[String, JLong, JLong],
        TimeWindows.of(60000),
        longSerde,
        WINDOWED_PAYLOAD_SIZE_PER_HOST_STORE
      )

    // materialize the summarized information into a topic
    payloadSize.to(stringSerde, longSerde, config.summaryPayloadTopic.get)
    windowedPayloadSize.to(windowedSerde, longSerde, config.windowedSummaryPayloadTopic.get)

    // print the topic info (for debugging)
    builder.stream(stringSerde, longSerde, config.summaryPayloadTopic.get).print()
  }
}
