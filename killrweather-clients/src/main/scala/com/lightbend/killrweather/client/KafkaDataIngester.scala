package com.lightbend.killrweather.client

import java.io.{BufferedReader, FileInputStream, InputStreamReader}
import java.util.zip.GZIPInputStream

import com.lightbend.killrweather.kafka.MessageSender
import org.apache.kafka.common.serialization.StringSerializer
import com.lightbend.killrweather.settings.WeatherSettings

import scala.collection.mutable.ListBuffer

/**
  * Created by boris on 7/7/17.
  */
object KafkaDataIngester {
  val file = "data/load/ny-2008.csv.gz"
  val timeInterval = 100 * 1        // 1 sec
  val batchSize = 10

  def main(args: Array[String]) {

    val settings = new WeatherSettings()

    import settings._

    val ingester = KafkaDataIngester("127.0.0.1:9092")
    ingester.execute(file, KafkaTopicRaw)
  }

  def apply(brokers: String): KafkaDataIngester = new KafkaDataIngester(brokers)
}

class KafkaDataIngester(brokers : String){

  val sender = MessageSender[String, String](brokers, classOf[StringSerializer].getName, classOf[StringSerializer].getName)

  import KafkaDataIngester._

  def execute(file: String, topic : String) : Unit = {

    val iterator = GzFileIterator(new java.io.File(file), "UTF-8")
    val batch = new ListBuffer[String]()
    var day = -1
    var numrec = 0;
    iterator.foreach(record => {
      numrec = numrec + 1
      batch += record
      if (batch.size >= batchSize) {
        sender.batchWriteValue(topic, batch)
        batch.clear()
        pause()
      }
      if(numrec % 100 == 0)
        println(s"Submitted $numrec records")
    })
    if(batch.size > 0)
      sender.batchWriteValue(topic, batch)

    println(s"Submitted $numrec records")
  }

  private def pause() : Unit = {
    try{
      Thread.sleep(timeInterval)
    }
    catch {
      case _: Throwable => // Ignore
    }
  }
}

class BufferedReaderIterator(reader: BufferedReader) extends Iterator[String] {
  override def hasNext() = reader.ready()
  override def next() = reader.readLine()
}

object GzFileIterator {
  def apply(file: java.io.File, encoding: String) = {
    new BufferedReaderIterator(
      new BufferedReader(
        new InputStreamReader(
          new GZIPInputStream(
            new FileInputStream(file)), encoding)))
  }
}