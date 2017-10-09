package com.lightbend.killrweather.loader.http

import com.lightbend.killrweather.loader.utils.{ DataConvertor, FilesIterator }

import scalaj.http.{ Http, HttpResponse }

/**
 * Created by boris on 7/7/17.
 */
object KafkaDataIngesterRest {

  val file = "data/load"
  val timeInterval: Long = 100 * 1 // 1 sec
  val batchSize = 10

  def main(args: Array[String]) {

    val url = "http://killrweatherhttpclient.marathon.mesos:5000/weather"
    //    val url = "http://localhost:5000/weather"

    val ingester = KafkaDataIngesterRest(url)
    ingester.execute(file)
  }

  def pause(): Unit = {
    try {
      Thread.sleep(timeInterval)
    } catch {
      case _: Throwable => // Ignore
    }
  }

  def apply(url: String): KafkaDataIngesterRest = new KafkaDataIngesterRest(url)
}

class KafkaDataIngesterRest(url: String) {

  import KafkaDataIngesterRest._

  def execute(file: String): Unit = {

    val sender = new HTTPSender(url)
    val iterator = FilesIterator(new java.io.File(file), "UTF-8")
    var numrec = 0;
    iterator.foreach(record => {
      //      println(s"Record : $record")
      numrec += 1
      sender.send(DataConvertor.convertToJson(record))
      if (numrec >= batchSize)
        pause()
      if (numrec % 100 == 0)
        println(s"Submitted $numrec records")
    })
    println(s"Submitted $numrec records")
  }
}

class HTTPSender(url: String) {
  val http = Http(url)

  def send(string: String): Unit = {
    try {
      val response: HttpResponse[String] = http.postData(string).header("content-type", "application/json").asString
      response.code match {
        case status if status / 10 == 20 =>
        case _ => {
          println(s"Unexpected return code ${response.code} with body ${response.body}")
        }
      }
    } catch {
      case e: Throwable => {
        println(s"Rest failed: ${e.printStackTrace()}")
        KafkaDataIngesterRest.pause()
      }
    }
  }
}