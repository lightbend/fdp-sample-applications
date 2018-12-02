package ingestion

import java.nio.file.{FileSystems, Path}

import scala.util.{Failure, Success}
import sys.process._
import com.typesafe.config.ConfigFactory
import akka.{Done, NotUsed}
import akka.util.ByteString
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Framing, Source}
import akka.stream.alpakka.file.DirectoryChange._
import akka.stream.alpakka.file.scaladsl._
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import com.lightbend.fdp.sample.flink.config.TaxiRideConfig.ConfigData
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.duration._
import scala.concurrent.Future
import com.lightbend.fdp.sample.flink.support.Serializers
import com.typesafe.scalalogging.LazyLogging
import com.lightbend.fdp.sample.flink.config.TaxiRideConfig._


object DataIngestion extends LazyLogging with Serializers {


  def main(args: Array[String]): Unit = {

    // get config info
    val config: ConfigData = fromConfig(ConfigFactory.load()) match {
      case Success(c)  => c
      case Failure(ex) => throw new Exception(ex)
    }

    println(s"Starting data ingester, kafka ${config.brokers}, reading from directory ${config.directoryToWatch}, writing to ${config.sourceTopic} topic")

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    import system.dispatcher

    // register for data ingestion
    // whenever we find new / changed files in the configured location, we run data loading
    registerForIngestion(config)

    val _ = system.scheduler.schedule(1 minute, 10 minute) {
      Seq("/bin/sh", "-c", s"touch ${config.directoryToWatch}/*.csv").!
      println("Updating data file")
    }
  }

  def registerForIngestion(config: ConfigData)
    (implicit system: ActorSystem, materializer: ActorMaterializer): Future[Done] = {

    val fs = FileSystems.getDefault

    DirectoryChangesSource(fs.getPath(config.directoryToWatch),
      config.pollInterval, 
      maxBufferSize = 1024).runForeach {

      case (path, x@(Creation | Modification)) if path.toString.endsWith(".csv") => {
        println(s"Got $path to process")
        val _ = produce(path, config)
        ()
      }
      case (_, Deletion) => ()
    }
  }
   
  private def produce(path: Path, config: ConfigData)
    (implicit system: ActorSystem, materializer: ActorMaterializer): NotUsed = {

    val MAX_CHUNK_SIZE = 25000
    val POLLING_INTERVAL = 250 millis

    val producerSettings = ProducerSettings(system, byteArraySerde.serializer, stringSerializer)
      .withBootstrapServers(config.brokers)
    
    val logLines: Source[String, NotUsed] =
      FileTailSource(path, MAX_CHUNK_SIZE, 0, POLLING_INTERVAL)
        .via(Framing.delimiter(ByteString.fromString("\n"), MAX_CHUNK_SIZE))
        .map(_.utf8String)

    logLines
      .map(new ProducerRecord[Array[Byte], String](config.sourceTopic, _))
      .to(Producer.plainSink(producerSettings))
      .run()
  }
}

