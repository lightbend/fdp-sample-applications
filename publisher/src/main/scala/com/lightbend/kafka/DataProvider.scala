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

  def main(args: Array[String]) {

    val config = ConfigFactory.load()
    val kafkaBrokers = config.getString("kafka.brokers")
    val zookeeperHosts = config.getString("zookeeper.hosts")
    val publisherConfig = config.getConfig("publisher")
    val dataTimeInterval: Duration = publisherConfig.getDuration("data_publish_interval")
    val modelTimeInterval: Duration = publisherConfig.getDuration("model_publish_interval")
    val dataDirectory = publisherConfig.getString("data_dir")
    val dataFilename = publisherConfig.getString("data_file")
    val dataFile = dataDirectory + "/" + dataFilename

    println(s"Data Provider with kafka brokers at $kafkaBrokers with zookeeper $zookeeperHosts")
    println(s"Data Message delay $dataTimeInterval")
    println(s"Model Message delay $modelTimeInterval")

    val dataPublisher = publishData(dataFile, dataTimeInterval, kafkaBrokers, zookeeperHosts)
    val modelPublisher = publishModels(dataDirectory, modelTimeInterval, kafkaBrokers, zookeeperHosts)

    val result = Future.firstCompletedOf(Seq(dataPublisher, modelPublisher))

    Await.result(result, InfiniteDuration)
  }

  def publishData(dataFileLocation: String, timeInterval: Duration, kafkaBrokers: String, zookeeperHosts: String): Future[Unit] = Future {
    println("Starting data publisher")
    val sender = KafkaMessageSender(kafkaBrokers, zookeeperHosts)
    sender.createTopic(DATA_TOPIC)
    val bos = new ByteArrayOutputStream()
    val records = getListOfRecords(dataFileLocation)
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

  def publishModels(dataDirectory: String, timeInterval: Duration, kafkaBrokers: String, zookeeperHosts: String): Future[Unit] = Future {
    println("Starting model publisher")
    val sender = KafkaMessageSender(kafkaBrokers, zookeeperHosts)
    sender.createTopic(MODELS_TOPIC)
    val files = listFilesWithExtension(dataDirectory, ".pmml")
    println(s"Models found: [${files.size}] => ${files.mkString(",")}")
    val bos = new ByteArrayOutputStream()
    while (true) {
      files.foreach(f => {
        // PMML
        println(s"Publishing model found on file: $f")
        val pByteArray = Files.readAllBytes(Paths.get(dataDirectory, f))
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
    Source.fromFile(file).getLines.map(WineRecordOps.toWineRecord).toSeq
  }

  private def listFilesWithExtension(dir: String, extension: String): Seq[String] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(f => (f.isFile) && (f.getName.endsWith(extension))).map(_.getName)
    } else {
      Seq.empty[String]
    }
  }
}

object WineRecordOps {
  def toWineRecord(str: String): WineRecord = {
    val cols = str.split(";").map(col => col.trim.toDouble)
    new WineRecord(
      fixedAcidity = cols(0),
      volatileAcidity = cols(1),
      citricAcid = cols(2),
      residualSugar = cols(3),
      chlorides = cols(4),
      freeSulfurDioxide = cols(5),
      totalSulfurDioxide = cols(6),
      density = cols(7),
      pH = cols(8),
      sulphates = cols(9),
      alcohol = cols(10),
      dataType = "wine"
    )
  }
}