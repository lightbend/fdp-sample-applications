package com.lightbend.killrweather.loader.kafka

import java.io.File

import com.lightbend.killrweather.loader.utils.{ DataConvertor, FilesIterator }
import com.lightbend.killrweather.kafka.MessageSender
import com.lightbend.killrweather.settings.WeatherSettings
import org.apache.kafka.common.serialization.ByteArraySerializer

import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer

object KafkaDataIngester {
  val file = "data/load/"
  val timeInterval = 1.second
  val batchSize = 10

  def main(args: Array[String]) {
    val kafkaConfig = WeatherSettings("DataIngester", args).kafkaConfig
    val ingester = KafkaDataIngester(kafkaConfig.brokers)
    println(s"Running Kafka Loader. Kafka: $brokers")
    ingester.execute(file, kafkaConfig.topic)
  }

  def pause(): Unit = Thread.sleep(timeInterval.toMillis)

  def apply(brokers: String): KafkaDataIngester = new KafkaDataIngester(brokers)
}

class KafkaDataIngester(brokers: String) {

  var sender = MessageSender[Array[Byte], Array[Byte]](brokers, classOf[ByteArraySerializer].getName, classOf[ByteArraySerializer].getName)

  import KafkaDataIngester._

  def execute(file: String, topic: String): Unit = {

    while (true) {
      val iterator = FilesIterator(new File(file))
      val batch = new ListBuffer[Array[Byte]]()
      var numrec = 0
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
}
