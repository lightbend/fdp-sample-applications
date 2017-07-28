package com.lightbend.killrweather.client.file

import java.io.{BufferedReader, ByteArrayOutputStream, FileInputStream, InputStreamReader}
import java.util.zip.GZIPInputStream

import com.lightbend.killrweather.WeatherClient.WeatherRecord
import com.lightbend.killrweather.kafka.MessageSender
import com.lightbend.killrweather.settings.WeatherSettings
import com.lightbend.killrweather.utils.RawWeatherData
import org.apache.kafka.common.serialization.ByteArraySerializer

import scala.collection.mutable.ListBuffer

/**
  * Created by boris on 7/7/17.
  */
object KafkaDataIngester {
  val file = "data/load/ny-2008.csv.gz"
  val timeInterval = 100 * 1        // 1 sec
  val batchSize = 10
  private val bos = new ByteArrayOutputStream()

  def main(args: Array[String]) {

    val settings = new WeatherSettings()

    import settings._

    val ingester = KafkaDataIngester("127.0.0.1:9092")
    ingester.execute(file, KafkaTopicRaw)
  }

  def apply(brokers: String): KafkaDataIngester = new KafkaDataIngester(brokers)
}

class KafkaDataIngester(brokers : String){

  val sender = MessageSender[Array[Byte], Array[Byte]](brokers, classOf[ByteArraySerializer].getName, classOf[ByteArraySerializer].getName)

  import KafkaDataIngester._

  def execute(file: String, topic : String) : Unit = {

    val iterator = GzFileIterator(new java.io.File(file), "UTF-8")
    val batch = new ListBuffer[Array[Byte]]()
    var numrec = 0;
    iterator.foreach(record => {

      numrec = numrec + 1
      batch += convertRecord(record)
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

  def convertRecord(string: String) : Array[Byte] = {
    bos.reset
    val report = RawWeatherData(string.split(","))
    WeatherRecord(report.wsid,report.year, report.month, report.day, report.hour, report.temperature,
      report.dewpoint, report.pressure, report.windDirection, report.windSpeed, report.skyCondition,
      report.skyConditionText, report.oneHourPrecip, report.sixHourPrecip).writeTo(bos)
    bos.toByteArray
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