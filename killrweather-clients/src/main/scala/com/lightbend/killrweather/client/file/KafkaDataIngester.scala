package com.lightbend.killrweather.client.file

import com.lightbend.killrweather.client.utils.{DataConvertor, GzFileIterator, ZipFileIterator}
import com.lightbend.killrweather.kafka.MessageSender
import com.lightbend.killrweather.settings.WeatherSettings
import org.apache.kafka.common.serialization.ByteArraySerializer

import scala.collection.mutable.ListBuffer

/**
 * Created by boris on 7/7/17.
 */
object KafkaDataIngester {
//  val file = "data/load/ny-2008.csv.gz"
  val file = "data/load/sfo-nyc-mia-lax-chi-2008-2014.csv.zip"
  val timeInterval: Long = 100 * 1 // 1 sec
  val batchSize = 10

  def main(args: Array[String]) {

    val settings = new WeatherSettings()

    import settings._

    val ingester = KafkaDataIngester( /*kafkaBrokers*/ "10.8.0.24:9757")
    ingester.execute(file, KafkaTopicRaw)
  }

  def apply(brokers: String): KafkaDataIngester = new KafkaDataIngester(brokers)
}

class KafkaDataIngester(brokers: String) {

  val sender = MessageSender[Array[Byte], Array[Byte]](brokers, classOf[ByteArraySerializer].getName, classOf[ByteArraySerializer].getName)

  import KafkaDataIngester._

  def execute(file: String, topic: String): Unit = {

//    val iterator = GzFileIterator(new java.io.File(file), "UTF-8")
    val iterator = ZipFileIterator(new java.io.File(file), "UTF-8")
    val batch = new ListBuffer[Array[Byte]]()
    var numrec = 0;
    iterator.foreach(record => {
      numrec = numrec + 1
      batch += DataConvertor.convertToGPB(record)
      if (batch.size >= batchSize) {
        sender.batchWriteValue(topic, batch)
        batch.clear()
        pause()
      }
      if (numrec % 100 == 0)
        println(s"Submitted $numrec records")
    })
    if (batch.size > 0)
      sender.batchWriteValue(topic, batch)
    println(s"Submitted $numrec records")
  }

  private def pause(): Unit = {
    try {
      Thread.sleep(timeInterval)
    } catch {
      case _: Throwable => // Ignore
    }
  }
}