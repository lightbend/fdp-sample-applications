package com.lightbend.fdp.sample.nwintrusion.ingestion

import java.io.{ StringWriter, PrintWriter }
import java.util.Properties

import scala.util.{ Try, Success, Failure }
import scala.concurrent.duration._
import scala.sys.process._

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import org.apache.kafka.streams.{ StreamsConfig, KafkaStreams }
import org.apache.kafka.clients.consumer.ConsumerConfig

import com.lightbend.kafka.scala.streams._
import ImplicitConversions._
import DefaultSerdes._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import IngestionConfig._


/**
 * The entry point of the streaming pipeline. Reads from a Kafka topic, does some
 * transformations and writes to another topic. All Kafka related settings come from
 * the config.
 */ 
object TransformIntrusionData extends LazyLogging {

  def main(args: Array[String]): Unit = {

    // get config info
    val config: ConfigData = fromConfig(ConfigFactory.load()) match {
      case Success(c)  => c
      case Failure(ex) => throw new Exception(ex)
    }

    println(s"Starting ingestion service with config: $config")
    logger.info(s"Starting ingestion service with config: $config")

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    import system.dispatcher

    // register for data ingestion
    // whenever we find new / changed files in the configured location, we run data loading
    DataIngestion.registerForIngestion(config)

    val _ = system.scheduler.scheduleOnce(1 minute) {
      Seq("/bin/sh", "-c", s"touch ${config.directoryToWatch}/*.csv").!
      ()
    }
    
    // stream it
    try {
      startKafkaStream(config)
    } catch {
      case e: Exception => e.printStackTrace
    }
  }  
  
  def startKafkaStream(config: ConfigData): Unit = {

    // Kafka stream configuration
    val streamingConfig = {
      val settings = new Properties
      settings.put(StreamsConfig.APPLICATION_ID_CONFIG, "transform-intrusion-data")
      settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, config.brokers)
      settings.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      settings
    }
    implicit val tuple2StringSerde = new Tuple2StringSerde

    val builder = new StreamsBuilderS

    // will read network data from `fromTopic`
    val filtered: Array[KStreamS[Array[Byte], Extracted]] = builder.stream[Array[Byte], String](config.fromTopic)
    
      // extract values after transformation
      .mapValues(extractor)
           
      // need to separate labelled data and errors
      .branch(predicateLabelled, predicateErrors)

    // push the labelled data
    filtered(0).mapValues(simpleMapper).to(config.toTopic)

    // push the extraction errors
    filtered(1).mapValues(errorMapper).to(config.errorTopic)

    // start streaming
    val stream = new KafkaStreams(builder.build, streamingConfig)

    stream.start()
  }

  abstract class Extracted(val value: String)  { }

  case class Labelled(label: String, v: String) extends Extracted(v)
  case class Error(exception: Exception, v: String) extends Extracted(v)

  val extractor: String => Extracted = { value =>
    try {

      // the fields 1-3 contains non-numeric features which k-means will
      // not be able to handle. Hence we remove them as part of extraction

      val arr = value.split(",")
      val consolidateCols6To21 = arr.slice(6, 22).map(_.toFloat).sum
      val consolidateCols24To27 = arr.slice(24, 27).map(_.toFloat).sum
      val consolidateCols37To40 = arr.slice(37, 40).map(_.toFloat).sum
      val buf = arr.toBuffer
      buf.remove(37, 4)
      buf.remove(24, 4)
      buf.remove(6, 16)
      buf.remove(1, 3)
      buf += consolidateCols6To21.toString
      buf += consolidateCols24To27.toString
      buf += consolidateCols37To40.toString

      // get the label after dropping the trailing dot(.)
      val label = dropTrailing(buf.remove(buf.length-4), ".")
      Labelled(label, buf.mkString(","))

    } catch {
      case e: Exception => Error(e, value)
    }
  }

  def dropTrailing(from: String, what: String) =
    if (from endsWith what) from.dropRight(what.length)
    else from


  // filters
  val predicateLabelled: (Array[Byte], Extracted) => Boolean = { (key, value) =>
    value match {
      case i: Labelled => true
      case _ => false
    }
  }

  val predicateErrors: (Array[Byte], Extracted) => Boolean = { (key, value) =>
    value match {
      case i: Error => true
      case _ => false
    }
  }

  val simpleMapper: Extracted => (String, String) = { value =>
    value match {
      case Labelled(l, v) => (l, v)
      case _ => ("**undefined**", "**undefined**")
    }
  }
  
  val errorMapper: Extracted => (String, String) = { value =>
    value match {
      case Error(e, v) =>
        val writer = new StringWriter()
        e.printStackTrace(new PrintWriter(writer))
        (writer.toString, v)
      case _ => ("**undefined**", "**undefined**")
    }
  }
}
