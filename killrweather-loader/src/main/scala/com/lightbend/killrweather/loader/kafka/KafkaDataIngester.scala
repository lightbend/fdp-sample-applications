package com.lightbend.killrweather.loader.kafka

import com.lightbend.killrweather.loader.utils.{ DataConvertor, FilesIterator }
import com.lightbend.killrweather.kafka.MessageSender
import com.lightbend.killrweather.settings.WeatherSettings
import org.apache.kafka.common.serialization.ByteArraySerializer

import scala.collection.mutable.ListBuffer

/**
 * Created by boris on 7/7/17.
 */
object KafkaDataIngester {
  val file = "data/load/"
  val timeInterval: Long = 100 * 1 // 1 sec
  val batchSize = 10

  def main(args: Array[String]) {

    WeatherSettings.handleArgs("KafkaDataIngester", args)

    val settings = new WeatherSettings()

    val ingester = KafkaDataIngester(settings.kafkaBrokers)
    ingester.execute(file, settings.KafkaTopicRaw)
  }

  def pause(): Unit = {
    try {
      Thread.sleep(timeInterval)
    } catch {
      case _: Throwable => // Ignore
    }
  }

  def apply(brokers: String): KafkaDataIngester = new KafkaDataIngester(brokers)
}

class KafkaDataIngester(brokers: String) {

  var sender = MessageSender[Array[Byte], Array[Byte]](brokers, classOf[ByteArraySerializer].getName, classOf[ByteArraySerializer].getName)

  import KafkaDataIngester._

  def execute(file: String, topic: String): Unit = {

    val iterator = FilesIterator(new java.io.File(file), "UTF-8")
    val batch = new ListBuffer[Array[Byte]]()
    var numrec = 0;
    iterator.foreach(record => {
      numrec += 1
      batch += DataConvertor.convertToGPB(record)
      if (batch.size >= batchSize) {
        try {
          if (sender == null)
            sender = MessageSender[Array[Byte], Array[Byte]](brokers, classOf[ByteArraySerializer].getName, classOf[ByteArraySerializer].getName)
          sender.batchWriteValue(topic, batch)
          batch.clear()
        } catch {
          case e: Throwable =>
            println(s"Kafka failed: ${e.printStackTrace()}")
            if (sender != null)
              sender.close()
            sender = null
        }
        pause()
      }
      if (numrec % 100 == 0)
        println(s"Submitted $numrec records")
    })
    if (batch.size > 0)
      sender.batchWriteValue(topic, batch)
    println(s"Submitted $numrec records")
  }
}
