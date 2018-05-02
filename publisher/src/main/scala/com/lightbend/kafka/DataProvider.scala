package com.lightbend.kafka

import java.io.{ ByteArrayOutputStream, File }
import java.nio.file.{ Files, Paths }

import com.google.protobuf.ByteString
import com.lightbend.configuration.AppParameters._
import com.lightbend.model.modeldescriptor.ModelDescriptor
import com.lightbend.model.winerecord.WineRecord
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global
import java.time.Duration

import scala.concurrent.duration.Duration.{ Inf => InfiniteDuration }
import scala.util.Try

/**
 *
 * Application publishing models from /data directory to Kafka
 */
object DataProvider {

  def main(args: Array[String]) {

    val config = ConfigFactory.load()
    val kafkaBrokers = config.getString("kafka.brokers")
    //    val zookeeperHosts = config.getString("zookeeper.hosts")
    val publisherConfig = config.getConfig("publisher")
    val dataTimeInterval: Duration = publisherConfig.getDuration("data_publish_interval")
    val modelTimeInterval: Duration = publisherConfig.getDuration("model_publish_interval")
    val dataDirectory = publisherConfig.getString("data_dir")
    val dataFilename = publisherConfig.getString("data_file")
    val dataFile = dataDirectory + "/" + dataFilename

    println(s"Data Provider with kafka brokers at $kafkaBrokers")
    println(s"Data Message delay $dataTimeInterval.  Model Message delay $modelTimeInterval")
    println(s"Data directory $dataDirectory. Data file name $dataFilename")

    val sender = new KafkaMessageSender(kafkaBrokers /*, zookeeperHosts*/ )

    val dataPublisher = publishData(sender, dataFile, dataTimeInterval, kafkaBrokers /*, zookeeperHosts*/ )
    val modelPublisher = publishModels(sender, dataDirectory, modelTimeInterval, kafkaBrokers /*, zookeeperHosts*/ )

    val result = Future.firstCompletedOf(Seq(dataPublisher, modelPublisher))

    Await.result(result, InfiniteDuration)
  }

  def publishData(sender: KafkaMessageSender, dataFileLocation: String,
    timeInterval: Duration, kafkaBrokers: String /*, zookeeperHosts: String*/ ): Future[Unit] = {
    println("Starting data publisher")
    Future {
      val bos = new ByteArrayOutputStream()
      val records = parseRecords(dataFileLocation)
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
  }

  def publishModels(sender: KafkaMessageSender, dataDirectory: String, timeInterval: Duration,
    kafkaBrokers: String /*, zookeeperHosts: String*/ ): Future[Unit] = {
    println("Starting model publisher")
    val tensorfile = s"${dataDirectory}/optimized_WineQuality.pb"
    Future {
      val files = listFilesWithExtension(dataDirectory, ".pmml")
      println(s"Models found: [${files.size}] => ${files.mkString(",")}")
      val bos = new ByteArrayOutputStream()
      while (true) {
        files.foreach(f => {
          // PMML
          println(s"Publishing model found on file: $f")
          val pByteArray = Files.readAllBytes(f.toPath)
          val pRecord = ModelDescriptor(
            name = dropExtension(f, "pmml"),
            description = "generated from SparkML", modeltype = ModelDescriptor.ModelType.PMML,
            dataType = "wine"
          ).withData(ByteString.copyFrom(pByteArray))
          bos.reset()
          pRecord.writeTo(bos)
          sender.writeValue(MODELS_TOPIC, bos.toByteArray)
          pause(timeInterval)
        })
        // TF
        val tByteArray = Files.readAllBytes(Paths.get(tensorfile))
        val tRecord = ModelDescriptor(
          name = tensorfile.dropRight(3),
          description = "generated from TensorFlow", modeltype = ModelDescriptor.ModelType.TENSORFLOW,
          dataType = "wine"
        ).withData(ByteString.copyFrom(tByteArray))
        bos.reset()
        tRecord.writeTo(bos)
        sender.writeValue(MODELS_TOPIC, bos.toByteArray)
        println(s"Published Model ${tRecord.description}")
        pause(timeInterval)

      }
    }
  }

  private def pause(timeInterval: Duration): Unit = Thread.sleep(timeInterval.toMillis)

  def parseRecords(file: String): Seq[WineRecord] = {
    Source.fromFile(file).getLines.map(WineRecordOps.toWineRecord).toSeq
  }

  private def listFilesWithExtension(dir: String, extension: String): Seq[File] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(f => (f.isFile) && (f.getName.endsWith(extension)))
    } else {
      Seq.empty
    }
  }

  def dropExtension(file: File, extension: String): String = file.getName.dropRight(extension.size)
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