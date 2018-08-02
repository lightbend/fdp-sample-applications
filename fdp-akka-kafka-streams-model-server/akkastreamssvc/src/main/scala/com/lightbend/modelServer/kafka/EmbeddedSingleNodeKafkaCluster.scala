package com.lightbend.modelServer.kafka

import org.slf4j.LoggerFactory
import org.apache.curator.test.TestingServer
import java.util.Properties

import kafka.server.KafkaConfig

import scala.util.{ Failure, Try }

object EmbeddedSingleNodeKafkaCluster {
  private val log = LoggerFactory.getLogger("EmbeddedSingleNodeKafkaCluster")
  private val DEFAULT_BROKER_PORT = 9092
  private var zookeeper: Option[TestingServer] = None
  private var broker: Option[KafkaEmbedded] = None

  /**
   * Creates and starts a Kafka cluster.
   */
  def start(): Unit = {
    broker = broker match {
      case bkr @ Some(b) =>
        log.debug("Broker already started")
        bkr
      case None =>
        val server = startZookeeper().flatMap { zk =>
          log.debug("Initiating embedded Kafka cluster startup")
          val brokerConfig = {
            val p = new Properties
            p.put(KafkaConfig.ZkConnectProp, zKConnectString)
            p.put(KafkaConfig.PortProp, DEFAULT_BROKER_PORT.toString)
            p
          }
          log.debug(s"Starting a Kafka instance on port ${brokerConfig.getProperty(KafkaConfig.PortProp)} ...")
          Try {
            val _broker = new KafkaEmbedded(brokerConfig)
            _broker.start()
            log.debug(s"Kafka instance is running at ${_broker.brokerList}, connected to ZooKeeper at ${_broker.zookeeperConnect}")
            _broker
          }.recoverWith {
            case ex: Throwable =>
              log.error(s"Unable to initialize Kafka instance. Reason: ${ex.getMessage}")
              Failure(ex)
          }
        }
        Some(server.get) // surface exceptions if any
    }
  }

  def startZookeeper(): Try[TestingServer] = {
    Try {
      val zk = new TestingServer(2181)
      log.debug("Starting a ZooKeeper instance")
      log.debug(s"ZooKeeper instance is running at ${zk.getConnectString}")
      zk
    }.recoverWith {
      case ex: Throwable =>
        log.error(s"Unable to initialize ZooKeeper instance. Reason: ${ex.getMessage}")
        Failure(ex)
    }
  }

  /**
   * Stop the Kafka cluster.
   */

  def stop(): Unit = {
    try {
      broker.foreach(_.stop())
      zookeeper.foreach(_.stop())

    } catch {
      case t: Throwable => ()
    }
    broker = None
    zookeeper = None
  }

  /**
   * The ZooKeeper connection string aka `zookeeper.connect` in `hostnameOrIp:port` format.
   * Example: `127.0.0.1:2181`.
   *
   * You can use this to e.g. tell Kafka brokers how to connect to this instance.
   */
  def zKConnectString: String = zookeeper.map(_.getConnectString).getOrElse("")

  /**
   * This cluster's `bootstrap.servers` value.  Example: `127.0.0.1:9092`.
   *
   * You can use this to tell Kafka producers how to connect to this cluster.
   */
  def bootstrapServers: String = broker.map(_.brokerList).getOrElse("")

  /**
   * Create a Kafka topic with the given parameters.
   *
   * @param topic       The name of the topic.
   * @param partitions  The number of partitions for this topic. Default (1)
   * @param replication The replication factor for (partitions of) this topic. Default (1)
   * @param topicConfig Additional topic-level configuration settings. Default (Empty properties)
   */
  def createTopic(topic: String, partitions: Int = 1, replication: Int = 1, topicConfig: Properties = new Properties): Unit = {
    broker.foreach(_.createTopic(topic, partitions, replication, topicConfig))
  }

}
