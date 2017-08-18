package com.lightbend.killrweather.client.http

import java.io.{ BufferedReader, ByteArrayOutputStream, FileInputStream, InputStreamReader }
import java.util.zip.GZIPInputStream

import com.lightbend.killrweather.settings.WeatherSettings
import com.lightbend.killrweather.utils.RawWeatherData

import org.json4s._
import org.json4s.jackson.Serialization.write

import scalaj.http.Http

/**
 * Created by boris on 7/7/17.
 */
object KafkaDataIngesterRest {

  implicit val formats = DefaultFormats
  val file = "data/load/ny-2008.csv.gz"
  val timeInterval: Long = 100 * 1 // 1 sec
  val batchSize = 10

  def main(args: Array[String]) {

    val settings = new WeatherSettings()
    val url = "http://10.8.0.19:5000/weather"
    //    val url = "http://localhost:5000/weather"

    val ingester = KafkaDataIngesterRest(url)
    ingester.execute(file)
  }

  def apply(url: String): KafkaDataIngesterRest = new KafkaDataIngesterRest(url)

  def convertRecord(string: String): String = {
    val report = RawWeatherData(string.split(","))
    write(report)
  }
}

class KafkaDataIngesterRest(url: String) {

  import KafkaDataIngesterRest._

  def execute(file: String): Unit = {

    val iterator = GzFileIterator(new java.io.File(file), "UTF-8")
    var numrec = 0;
    iterator.foreach(record => {
      //      println(s"Record : $record")
      numrec = numrec + 1
      Http(url).postData(convertRecord(record)).header("content-type", "application/json").asString
      if (numrec >= batchSize)
        pause()
      if (numrec % 100 == 0)
        println(s"Submitted $numrec records")
    })
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
            new FileInputStream(file)
          ), encoding
        )
      )
    )
  }
}