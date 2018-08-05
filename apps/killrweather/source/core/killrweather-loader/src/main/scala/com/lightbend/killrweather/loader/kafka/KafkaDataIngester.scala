package com.lightbend.killrweather.loader.kafka

import java.io.File

import com.lightbend.killrweather.loader.utils.{DataConvertor, FilesIterator}
import com.lightbend.killrweather.kafka.MessageSender
import com.lightbend.killrweather.settings.WeatherSettings
import org.apache.kafka.common.serialization.ByteArraySerializer

import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer

object KafkaDataIngester {

  def main(args: Array[String]) {

    val killrSettings = WeatherSettings("KillrWeather", args)
    import killrSettings._

    val brokers = kafkaConfig.brokers
    val dataDir = loaderConfig.data_dir
    val timeInterval = Duration(loaderConfig.publish_interval)
    val batchSize = loaderConfig.batch_size
    println(s"Starting data ingester \n Brokers : $brokers, topic : ${kafkaConfig.topic}, directory : $dataDir, timeinterval $timeInterval, batch size $batchSize")

    val ingester = KafkaDataIngester(brokers, batchSize, timeInterval)

    println(s"Running Kafka Loader. Kafka: $brokers")


    ingester.execute(dataDir, kafkaConfig.topic)
  }

  def pause(timeInterval : Duration): Unit = Thread.sleep(timeInterval.toMillis)

  def apply(brokers: String, batchSize: Int, timeInterval : Duration): KafkaDataIngester = new KafkaDataIngester(brokers, batchSize, timeInterval)
}

class KafkaDataIngester(brokers: String, batchSize: Int, timeInterval : Duration) {

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
          pause(timeInterval)
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
