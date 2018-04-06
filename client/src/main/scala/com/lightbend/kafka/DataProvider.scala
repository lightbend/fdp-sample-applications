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
import scala.concurrent.duration._

/**
 *
 * Application publishing models from /data directory to Kafka
 */
object DataProvider {

  val file = "data/winequality_red.csv"
  val DataTimeInterval = 1.second
  val ModelTimeInterval = 5.minutes
  val directory = "data/"

  def main(args: Array[String]) {

    val config = ConfigFactory.load()
    val kafkaBrokers = config.getString("kafka.brokers")
    val zookeeperHosts = config.getString("zookeeper.hosts")

    println(s"Data Provider with kafka brokers at $kafkaBrokers with zookeeper $zookeeperHosts")
    val dataTimeInterval = if (args.length > 0) args(0).toInt.millis else DataTimeInterval
    val modelTimeInterval = if (args.length > 1) args(1).toInt.millis else ModelTimeInterval
    println(s"Data Message delay $dataTimeInterval")
    println(s"Model Message delay $modelTimeInterval")

    val dataPublisher = publishData(dataTimeInterval, kafkaBrokers, zookeeperHosts)
    val modelPublisher = publishModels(modelTimeInterval, kafkaBrokers, zookeeperHosts)

    val result = Future.firstCompletedOf(Seq(dataPublisher, modelPublisher))

    Await.result(result, Duration.Inf)
  }

  def publishData(timeInterval: Duration, kafkaBrokers: String, zookeeperHosts: String): Future[Unit] = Future {
    val sender = KafkaMessageSender(kafkaBrokers, zookeeperHosts)
    sender.createTopic(DATA_TOPIC)
    val bos = new ByteArrayOutputStream()
    val records = getListOfRecords(file)
    var nrec = 0
    while (true) {
      records.foreach(r => {
        bos.reset()
        r.writeTo(bos)
        sender.writeValue(DATA_TOPIC, bos.toByteArray)
        nrec = nrec + 1
        if (nrec % 10 == 0) println(s"printed $nrec records")
        pause(timeInterval)
      })
    }
  }

  def publishModels(timeInterval: Duration, kafkaBrokers: String, zookeeperHosts: String): Future[Unit] = Future {
    val sender = KafkaMessageSender(kafkaBrokers, zookeeperHosts)
    sender.createTopic(MODELS_TOPIC)
    val files = getListOfModelFiles(directory)
    val bos = new ByteArrayOutputStream()
    while (true) {
      files.foreach(f => {
        // PMML
        val pByteArray = Files.readAllBytes(Paths.get(directory + f))
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