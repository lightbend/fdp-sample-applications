package com.lightbend.kafka

/**
 * Byte array sender to Kafka
 */

import java.util.Properties

import kafka.admin.AdminUtils
import kafka.utils.ZkUtils
import org.apache.kafka.clients.producer.{ KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata }
import org.apache.kafka.common.serialization.ByteArraySerializer
import scala.concurrent.duration._

class KafkaMessageSender(brokers: String, zookeeper: String) {

  // Configure
  val props = {
    val p = new Properties
    p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
    p.put(ProducerConfig.ACKS_CONFIG, KafkaMessageSender.ACKCONFIGURATION)
    p.put(ProducerConfig.RETRIES_CONFIG, KafkaMessageSender.RETRYCOUNT)
    p.put(ProducerConfig.BATCH_SIZE_CONFIG, KafkaMessageSender.BATCHSIZE)
    p.put(ProducerConfig.LINGER_MS_CONFIG, KafkaMessageSender.LINGERTIME)
    p.put(ProducerConfig.BUFFER_MEMORY_CONFIG, KafkaMessageSender.BUFFERMEMORY)
    p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[ByteArraySerializer].getName)
    p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[ByteArraySerializer].getName)
    p
  }

  private val sessionTimeout = 10.seconds.toMillis.toInt
  private val connectionTimeout = 8.seconds.toMillis.toInt

  // Create producer
  val producer = new KafkaProducer[Array[Byte], Array[Byte]](props)
  val zkUtils = ZkUtils.apply(zookeeper, sessionTimeout, connectionTimeout, isZkSecurityEnabled = false)

  // Write value to the queue
  def writeValue(topic: String, value: Array[Byte]): RecordMetadata = {
    val result = producer.send(new ProducerRecord[Array[Byte], Array[Byte]](topic, null, value)).get
    producer.flush()
    result
  }

  // Close producer
  def close(): Unit = {
    producer.close
  }

  def createTopic(topic: String, numPartitions: Int = 1, replicationFactor: Int = 1): Unit = {
    if (!AdminUtils.topicExists(zkUtils, topic)) {
      try {
        AdminUtils.createTopic(zkUtils, topic, numPartitions, replicationFactor)
        println(s"Topic $topic with $numPartitions partitions and replication factor $replicationFactor is created")
      } catch {
        case t: Throwable => println(s"Failed to create topic $topic. ${t.getMessage}")
      }
    } else
      println(s"Topic $topic already exists")
  }
}

object KafkaMessageSender {
  private val ACKCONFIGURATION = "all" // Blocking on the full commit of the record
  private val RETRYCOUNT = "1" // Number of retries on put
  private val BATCHSIZE = "1024" // Buffers for unsent records for each partition - controlls batching
  private val LINGERTIME = "1" // Timeout for more records to arive - controlls batching
  private val BUFFERMEMORY = "1024000" // Controls the total amount of memory available to the producer for buffering. If records are sent faster than they can be transmitted to the server then this buffer space will be exhausted. When the buffer space is exhausted additional send calls will block. The threshold for time to block is determined by max.block.ms after which it throws a TimeoutException.
  private var senders: Map[String, KafkaMessageSender] = Map() // Producer instances

  def apply(brokers: String, zookeeper: String): KafkaMessageSender = {
    senders.get(brokers).getOrElse {
      val sender = new KafkaMessageSender(brokers, zookeeper)
      senders = senders + (brokers -> sender)
      sender
    }
  }
}
