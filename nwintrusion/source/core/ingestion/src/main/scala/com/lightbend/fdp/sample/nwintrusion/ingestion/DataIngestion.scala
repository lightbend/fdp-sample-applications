package com.lightbend.fdp.sample.nwintrusion.ingestion

import java.nio.file.{ Path, FileSystems, Paths }

import akka.{ NotUsed, Done }
import akka.util.ByteString
import akka.actor.ActorSystem

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Framing, Source }
import akka.stream.alpakka.file.DirectoryChange._
import akka.stream.alpakka.file.scaladsl._

import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer

import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.duration._
import scala.concurrent.Future

import IngestionConfig._
import com.typesafe.scalalogging.LazyLogging
import com.lightbend.kafka.scala.streams.DefaultSerdes._

object DataIngestion extends LazyLogging { 
  def registerForIngestion(config: ConfigData)
    (implicit system: ActorSystem, materializer: ActorMaterializer): Future[Done] = {

    val fs = FileSystems.getDefault

    DirectoryChangesSource(Paths.get(config.directoryToWatch),
      config.pollInterval, 
      maxBufferSize = 1024).runForeach {

      case (path, x@(Creation | Modification)) => {
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

    val producerSettings = ProducerSettings(system, byteArraySerde.serializer, stringSerde.serializer)
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
