package com.lightbend.fdp.sample

import java.io.BufferedWriter
import java.io.StringWriter
import java.io.PrintWriter
import java.util.Properties

import scala.util.{ Try, Success, Failure }
import com.typesafe.config.ConfigFactory

import org.apache.kafka.streams.kstream.KStreamBuilder
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.kstream.Predicate
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams._
import org.apache.kafka.streams.kstream.{ KStream, ValueMapper, KeyValueMapper }

import NetworkIntrusionConfig._

/**
 * The entry point of the streaming pipeline. Reads from a Kafka topic, does some
 * transformations and writes to another topic. All Kafka related settings come from
 * the config.
 */ 
object TransformIntrusionData {

  def main(args: Array[String]): Unit = {
    
    // get config info
    val config: ConfigData = fromConfig(ConfigFactory.load()) match {
      case Success(c)  => c
      case Failure(ex) => throw new Exception(ex)
    }
    
    // stream it
    try {
      startKafkaStream(config)
    } catch {
      case e: Exception => e.printStackTrace
    }
  }  
  
  def startKafkaStream(config: ConfigData) {

    // Kafka stream configuration
    val streamingConfig = {
      val settings = new Properties
      settings.put(StreamsConfig.APPLICATION_ID_CONFIG, "transform-intrusion-data")
      settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, config.brokers)
      settings.put(StreamsConfig.ZOOKEEPER_CONNECT_CONFIG, config.zk)
      settings.put(StreamsConfig.KEY_SERDE_CLASS_CONFIG, Serdes.ByteArray.getClass.getName)
      settings.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, Serdes.String.getClass.getName)
      settings
    }

    val builder = new KStreamBuilder()

    // will read network data from `fromTopic`
    val logs: KStream[Array[Byte], String] = builder.stream(config.fromTopic)
    
    // extract values after transformation
    val extracted = logs.mapValues(extractor)
    
    // need to separate labelled data and errors
    val filtered = extracted.branch(predicateLabelled, predicateErrors)

    // push the labelled data
    filtered(0).mapValues(simpleMapper).to(Serdes.ByteArray, new Tuple2StringSerde, config.toTopic)
    
    // push the extraction errors
    filtered(1).mapValues(errorMapper).to(Serdes.ByteArray, new Tuple2StringSerde, config.errorTopic)

    // start streaming
    val stream = new KafkaStreams(builder, streamingConfig)

    stream.start()
  }

  abstract class Extracted(val value: String)  { }

  case class Labelled(label: String, v: String) extends Extracted(v)
  case class Error(exception: Exception, v: String) extends Extracted(v)

  val extractor = new ValueMapper[String, Extracted] {
    def apply(value: String): Extracted = try {

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
  val predicateLabelled = new Predicate[Array[Byte], Extracted] {
    def test(key: Array[Byte], value: Extracted): Boolean = {
      value match {
        case i: Labelled => true
        case _ => false
      }
    }
  }

  val predicateErrors = new Predicate[Array[Byte], Extracted] {
    def test(key: Array[Byte], value: Extracted): Boolean = {
      value match {
        case i: Error => true
        case _ => false
      }
    }
  }

  val simpleMapper = new ValueMapper[Extracted, (String, String)] {
    def apply(value: Extracted): (String, String) = value match {
      case Labelled(l, v) => (l, v)
      case _ => ("**undefined**", "**undefined**")
    }
  }
  
  val errorMapper = new ValueMapper[Extracted, (String, String)] {
    def apply(value: Extracted): (String, String) = value match {
      case Error(e, v) =>
        val writer = new StringWriter()
        e.printStackTrace(new PrintWriter(writer))
        (writer.toString, v)
      case _ => ("**undefined**", "**undefined**")
    }
  }
}
