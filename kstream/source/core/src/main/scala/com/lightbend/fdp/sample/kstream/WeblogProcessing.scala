package com.lightbend.fdp.sample.kstream

import com.typesafe.config.ConfigFactory

import java.io.{ StringWriter, PrintWriter }
import java.util.{ Properties, Locale }
import java.lang.{ Long => JLong }

import org.apache.kafka.common.serialization.{ Serde, Serdes }
import org.apache.kafka.streams.kstream.{ KStreamBuilder, KStream, ValueMapper, KeyValueMapper, KTable }
import org.apache.kafka.streams.kstream.{ Initializer, Aggregator, Predicate, TimeWindows, KGroupedStream, Windowed }
import org.apache.kafka.streams.{ StreamsConfig, KafkaStreams, KeyValue }
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.streams.state.{ ReadOnlyKeyValueStore, QueryableStoreTypes, QueryableStoreType }
import org.apache.kafka.streams.errors.InvalidStateStoreException
import org.apache.kafka.streams.state.HostInfo

import scala.util.{ Success, Failure }
import com.typesafe.scalalogging.LazyLogging

import config.KStreamConfig._
import serializers._
import models.{ LogRecord, LogParseUtil }
import http.WeblogMicroservice

object WeblogProcessing extends LazyLogging with CommandLineParser with Serializers {

  private final val DEFAULT_REST_ENDPOINT_HOSTNAME = "localhost"
  private final val DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092"

  final val ACCESS_COUNT_PER_HOST_STORE = "access-count-per-host"
  final val PAYLOAD_SIZE_PER_HOST_STORE = "payload-size-per-host"
  final val WINDOWED_ACCESS_COUNT_PER_HOST_STORE = "windowed-access-count-per-host"
  final val WINDOWED_PAYLOAD_SIZE_PER_HOST_STORE = "windowed-payload-size-per-host"

  def main(args: Array[String]): Unit = {
    
    var restService: WeblogMicroservice = null

    val cliConfig: CliConfig = 
      parseCommandLineArgs(args).getOrElse(throw new IllegalArgumentException("Invalid command line arguments specified"))

    // get config info
    val config: ConfigData = fromConfig(ConfigFactory.load()) match {
      case Success(c)  => c
      case Failure(ex) => throw ex
    }

    // setup REST endpoints
    val restEndpointPort = cliConfig.port
    val restEndpointHostName = cliConfig.host.getOrElse(DEFAULT_REST_ENDPOINT_HOSTNAME)
    val restEndpoint = new HostInfo(restEndpointHostName, restEndpointPort)

    logger.info("Connecting to Kafka cluster via bootstrap servers " + config.brokers)
    logger.info("REST endpoint at http://" + restEndpointHostName + ":" + restEndpointPort)

    // set up the topology
    val streams: KafkaStreams = createStreams(config, restEndpointPort, "/tmp/kafka-streams")

    // need to exit for any stream exception
    // mesos will restart the application
    streams.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      override def uncaughtException(t: Thread, e: Throwable): Unit = try {
        logger.error(s"Stream terminated because of uncaught exception .. Shutting down app", e)
        restService.stop()
        streams.close()
      } catch {
        case _: Exception => 
      } finally {
        System.exit(-1)
      }
    })

    // Need to be done for running the application after resetting the state store
    // should not be done in production
    streams.cleanUp()

    // Now that we have finished the definition of the processing topology we can actually run
    // it via `start()`.  The Streams application as a whole can be launched just like any
    // normal Java application that has a `main()` method.
    streams.start()

    // Start the Restful proxy for servicing remote access to state stores
    restService = startRestProxy(streams, restEndpoint)

    // Add shutdown hook to respond to SIGTERM and gracefully close Kafka Streams
    Runtime.getRuntime().addShutdownHook(new Thread(() => try {
      restService.stop()
      streams.close()
    } catch {
      case _: Exception => // ignored
    }))
  }  

  def startRestProxy(streams: KafkaStreams, hostInfo: HostInfo): WeblogMicroservice = {
    val restService = new WeblogMicroservice(streams, hostInfo)
    restService.start()
    restService
  }
  
  def createStreams(config: ConfigData, applicationServerPort: Int, stateStoreDir: String): KafkaStreams = {

    // Kafka stream configuration
    val streamingConfig = {
      val settings = new Properties
      settings.put(StreamsConfig.APPLICATION_ID_CONFIG, "kstream-log-processing")
      settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, config.brokers)
      settings.put(StreamsConfig.KEY_SERDE_CLASS_CONFIG, Serdes.ByteArray.getClass.getName)
      settings.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, Serdes.String.getClass.getName)

      // setting offset reset to earliest so that we can re-run the demo code with the same pre-loaded data
      // Note: To re-run the demo, you need to use the offset reset tool:
      // https://cwiki.apache.org/confluence/display/KAFKA/Kafka+Streams+Application+Reset+Tool
      settings.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

      // need this for query service
      settings.put(StreamsConfig.APPLICATION_SERVER_CONFIG, "localhost:" + applicationServerPort);

      // default is /tmp/kafka-streams
      settings.put(StreamsConfig.STATE_DIR_CONFIG, stateStoreDir);

      // Set the commit interval to 500ms so that any changes are flushed frequently and the summary
      // data are updated with low latency.
      settings.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "500");

      settings
    }

    val builder = new KStreamBuilder()

    generateLogRecords(builder, config)

    //
    // assumption : the topic contains serialized records of LogRecord
    val logRecords: KStream[Array[Byte], LogRecord] = builder.stream(byteArraySerde, logRecordSerde, config.toTopic.get)

    hostCountSummary(logRecords, builder, config)
    totalPayloadPerHostSummary(logRecords, builder, config)

    new KafkaStreams(builder, streamingConfig)
  }

  def generateLogRecords(builder: KStreamBuilder, config: ConfigData): Unit = {

    // will read network data from `fromTopic`
    val logs: KStream[Array[Byte], String] = builder.stream(config.fromTopic)
    
    // extract values after transformation
    val extracted: KStream[Array[Byte], Extracted] = logs.mapValues(extractor)
    
    // need to separate labelled data and errors
    val filtered: Array[KStream[Array[Byte], Extracted]] = extracted.branch(predicateValid, predicateErrors)

    // push the labelled data
    val v: KStream[Array[Byte], LogRecord] = filtered(0).mapValues(simpleMapper)
    v.to(byteArraySerde, logRecordSerde, config.toTopic.get)

    // push the extraction errors
    val i: KStream[Array[Byte], (String, String)] = filtered(1).mapValues(errorMapper)
    i.to(byteArraySerde, tuple2StringSerde, config.errorTopic)
  }

  sealed abstract class Extracted { }
  final case class ValidLogRecord(record: LogRecord) extends Extracted
  final case class ValueError(exception: Throwable, originalRecord: String) extends Extracted

  val extractor = new ValueMapper[String, Extracted] {
    def apply(record: String): Extracted =
      LogParseUtil.parseLine(record) match {
        case Success(r) => ValidLogRecord(r)
        case Failure(ex) => ValueError(ex, record)
      }
  }

  // filters
  val predicateValid = new Predicate[Array[Byte], Extracted] {
    def test(key: Array[Byte], value: Extracted): Boolean = {
      value match {
        case ValidLogRecord(_) => true
        case _ => false
      }
    }
  }

  val predicateErrors = new Predicate[Array[Byte], Extracted] {
    def test(key: Array[Byte], value: Extracted): Boolean = {
      value match {
        case ValueError(_, _) => true
        case _ => false
      }
    }
  }

  val simpleMapper = new ValueMapper[Extracted, LogRecord] {
    def apply(value: Extracted): LogRecord = value match {
      case ValidLogRecord(r) => r
      case _ => ???
    }
  }
  
  val errorMapper = new ValueMapper[Extracted, (String, String)] {
    def apply(value: Extracted): (String, String) = value match {
      case ValueError(e, v) =>
        val writer = new StringWriter()
        e.printStackTrace(new PrintWriter(writer))
        (writer.toString, v)
      case _ => ???
    }
  }


  /**
   * Summary count of number of times each host has been accessed
   */ 
  def hostCountSummary(logRecords: KStream[Array[Byte], LogRecord], builder: KStreamBuilder, config: ConfigData): Unit = {
    // we want to compute the number of times each host is accessed, hence get the host name
    val hosts: KStream[Array[Byte], String] = logRecords.mapValues(hostExtractor)

    // we are changing the key here so that we can do a groupByKey later
    val hostPairs: KStream[String, String] = hosts.map(
      new KeyValueMapper[Array[Byte], String, KeyValue[String, String]]() {
        override def apply(key: Array[Byte], value: String): KeyValue[String, String] = new KeyValue(value, value)
      }) 
    
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
    builder.stream(windowedSerde, longSerde, config.windowedSummaryAccessTopic.get).print()
  }

  val hostExtractor = new ValueMapper[LogRecord, String] {
    def apply(record: LogRecord): String = record.host
  }

  /**
   * Aggregate value of payloadSize per host
   */ 
  def totalPayloadPerHostSummary(logRecords: KStream[Array[Byte], LogRecord], builder: KStreamBuilder, config: ConfigData): Unit = {
    // we want to compute the number of times each host is accessed, hence get the host name
    val payloads: KStream[Array[Byte], (String, JLong)] = logRecords.mapValues(hostPayloadExtractor)

    // we are changing the key here so that we can do a groupByKey later
    val n: KStream[String, JLong] = payloads.map(
      new KeyValueMapper[Array[Byte], (String, JLong), KeyValue[String, JLong]]() {
        override def apply(key: Array[Byte], value: (String, JLong)): KeyValue[String, JLong] = new KeyValue(value._1, value._2)
      }) 
    
    val groupedStream: KGroupedStream[String, JLong] = n.groupByKey(stringSerde, longSerde)

    val payloadSize: KTable[String, JLong] = groupedStream
     .aggregate(
       new Initializer[JLong] {
         def apply() = 0L
       },
       new Aggregator[String, JLong, JLong] {
         def apply(k: String, s: JLong, agg: JLong) = s + agg
       },
       longSerde,
       PAYLOAD_SIZE_PER_HOST_STORE
     )

    val windowedPayloadSize: KTable[Windowed[String], JLong] = groupedStream
     .aggregate(
       new Initializer[JLong] {
         def apply() = 0L
       },
       new Aggregator[String, JLong, JLong] {
         def apply(k: String, s: JLong, agg: JLong) = s + agg
       },
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

  val hostPayloadExtractor = new ValueMapper[LogRecord, (String, JLong)] {
    def apply(record: LogRecord): (String, JLong) = (record.host, record.payloadSize)
  }
}
