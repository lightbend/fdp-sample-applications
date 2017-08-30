package com.lightbend.killrweather.client.http

import com.lightbend.killrweather.client.utils.{DataConvertor, GzFileIterator}
import com.lightbend.killrweather.settings.WeatherSettings

import scalaj.http.Http

/**
 * Created by boris on 7/7/17.
 */
object KafkaDataIngesterRest {

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
}

class KafkaDataIngesterRest(url: String) {

  import KafkaDataIngesterRest._

  def execute(file: String): Unit = {

    val iterator = GzFileIterator(new java.io.File(file), "UTF-8")
    var numrec = 0;
    iterator.foreach(record => {
      //      println(s"Record : $record")
      numrec = numrec + 1
      Http(url).postData(DataConvertor.convertToJson(record)).header("content-type", "application/json").asString
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