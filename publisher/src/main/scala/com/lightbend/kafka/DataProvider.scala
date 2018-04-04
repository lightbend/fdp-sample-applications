package com.lightbend.kafka

import java.io.{ ByteArrayOutputStream, File }
import java.nio.file.{ Files, Paths }

import com.google.protobuf.ByteString
import com.lightbend.configuration.AppParameters._
import com.lightbend.model.modeldescriptor.ModelDescriptor
import com.lightbend.model.winerecord.WineRecord
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ Await, Future }
import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global
import java.time.Duration
import scala.concurrent.duration.Duration.{ Inf => InfiniteDuration }

/**
 *
 * Application publishing models from /data directory to Kafka
 */
object DataProvider {

  val directory = "data"
  val DataFile = s"$directory/winequality_red.csv"

  def main(args: Array[String]) {

    val config = ConfigFactory.load()
    val kafkaBrokers = config.getString("kafka.brokers")
    val zookeeperHosts = config.getString("zookeeper.hosts")
    val publisherConfig = config.getConfig("publisher")
    val dataTimeInterval: Duration = publisherConfig.getDuration("data_publish_interval")
    val modelTimeInterval: Duration = publisherConfig.getDuration("model_publish_interval")

    println(s"Data Provider with kafka brokers at $kafkaBrokers with zookeeper $zookeeperHosts")
    println(s"Data Message delay $dataTimeInterval")
    println(s"Model Message delay $modelTimeInterval")

    val dataPublisher = publishData(dataTimeInterval, kafkaBrokers, zookeeperHosts)
    val modelPublisher = publishModels(modelTimeInterval, kafkaBrokers, zookeeperHosts)

    val result = Future.firstCompletedOf(Seq(dataPublisher, modelPublisher))

    Await.result(result, InfiniteDuration)
  }

  def publishData(timeInterval: Duration, kafkaBrokers: String, zookeeperHosts: String): Future[Unit] = Future {
    println("Starting data publisher")
    val sender = KafkaMessageSender(kafkaBrokers, zookeeperHosts)
    sender.createTopic(DATA_TOPIC)
    val bos = new ByteArrayOutputStream()
    val records = getListOfRecords(DataFile)
    println(s"Records found in data: ${records.size}")
    var nrec = 0
    while (true) {
      records.foreach(r => {
        bos.reset()
        r.writeTo(bos)
        sender.writeValue(DATA_TOPIC, bos.toByteArray)
        nrec = nrec + 1
        if (nrec % 10 == 0) println(s"produced $nrec data records")
        pause(timeInterval)
      })
    }
  }

  def publishModels(timeInterval: Duration, kafkaBrokers: String, zookeeperHosts: String): Future[Unit] = Future {
    println("Starting model publisher")
    val sender = KafkaMessageSender(kafkaBrokers, zookeeperHosts)
    sender.createTopic(MODELS_TOPIC)
    val files = getListOfModelFiles(directory)
    println(s"Models found: [${files.size}] => ${files.mkString(",")}")
    val bos = new ByteArrayOutputStream()
    while (true) {
      files.foreach(f => {
        // PMML
        println(s"Publishing model found on file: $f")
        val pByteArray = Files.readAllBytes(Paths.get(directory, f))
        val pRecord = ModelDescriptor(
          name = f.dropRight(5),
          description = "generated from SparkML", modeltype = ModelDescriptor.ModelType.PMML,
          dataType = "wine"
        ).withData(ByteString.copyFrom(pByteArray))
        bos.reset()
        pRecord.writeTo(bos)
        sender.writeValue(MODELS_TOPIC, bos.toByteArray)
        pause(timeInterval)
      })
    }

  }

  private def pause(timeInterval: Duration): Unit = Thread.sleep(timeInterval.toMillis)

  def getListOfRecords(file: String): Seq[WineRecord] = {
    Source.fromFile(file).getLines.map { line =>
      val cols = line.split(";").map(_.trim)
      new WineRecord(
        fixedAcidity = cols(0).toDouble,
        volatileAcidity = cols(1).toDouble,
        citricAcid = cols(2).toDouble,
        residualSugar = cols(3).toDouble,
        chlorides = cols(4).toDouble,
        freeSulfurDioxide = cols(5).toDouble,
        totalSulfurDioxide = cols(6).toDouble,
        density = cols(7).toDouble,
        pH = cols(8).toDouble,
        sulphates = cols(9).toDouble,
        alcohol = cols(10).toDouble,
        dataType = "wine"
      )
    }.toSeq
  }

  private def getListOfModelFiles(dir: String): Seq[String] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(f => (f.isFile) && (f.getName.endsWith(".pmml"))).map(_.getName)
    } else {
      Seq.empty[String]
    }
  }
}