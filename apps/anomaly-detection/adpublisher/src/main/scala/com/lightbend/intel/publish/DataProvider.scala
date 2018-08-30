package com.lightbend.ad.publish

import java.io.{ByteArrayOutputStream, File}
import java.nio.file.{Files, Paths}

import com.google.protobuf.ByteString

import scala.concurrent.duration.Duration
import com.lightbend.ad.configuration.IntelConfig
import com.typesafe.config.ConfigFactory
import com.lightbend.ad.generator.{MarkovChain, Model, Noise, RandomPulses}
import com.lightbend.ad.influx.InfluxDBSink
import com.lightbend.model.cpudata.CPUData
import com.lightbend.model.modeldescriptor.{ModelDescriptor, ModelPreprocessing}
import com.lightbend.ad.kafka._

import scala.util.Random

/**
 *
 * Application publishing generated models to Kafka
 */
object DataProvider {

  // Create context
  val settings = IntelConfig.fromConfig(ConfigFactory.load()).get
  import settings._


  def main(args: Array[String])= {

    val kafkaBrokers = kafkaDataConfig.brokers
    val dataTimeInterval = Duration(loaderConfig.publishinterval)

    println(s"Data Provider with kafka brokers at $kafkaBrokers")
    println(s"Data Message delay $dataTimeInterval")
    println(s"Model data located at ${loaderConfig.model}")

    val influxDBSink = InfluxDBSink()

    val data = signal()
    val sender = new KafkaMessageSender(kafkaBrokers )

    val dataPublisher = publishData(sender, dataTimeInterval, data, influxDBSink)
  }

  def signal(timeLength: Int = 50000): MarkovChain = {

    val generator = new Random()

    val STM : Array[Array[Double]] = Array(Array(.9, .1), Array(.5, .5))
    val basesignal = new Noise("normal", 0.3,.15)
    val anomalysignal = new Noise("normal", 0.6,.1)
    val basenoise = new RandomPulses(generator.nextDouble() * timeLength/15.0,  Math.abs(generator.nextDouble()) * .01)
    val anomalynoise = new RandomPulses(generator.nextDouble() * timeLength/15.0, Math.abs(generator.nextDouble()) * .01)

    val base = new Model(Seq((basesignal, 1), (basenoise,.1)))
    val anomaly = new Model(Seq((anomalysignal, 1), (anomalynoise,.1)))

    new MarkovChain(STM, Seq(base, anomaly), 3)
  }

  def publishData(sender: KafkaMessageSender, timeInterval: Duration, signal : MarkovChain, influxDBSink : InfluxDBSink): Unit = {
    println("Starting data publisher")
    val bos = new ByteArrayOutputStream()
//    getModel().writeTo(bos)
//    sender.writeValue(kafkaDataConfig.modeltopic, bos.toByteArray)
    println("Publishing data")
    while (true) {
      for( i <- 1 to 100) {
        val record = new CPUData(signal.next(), signal.getLabel(), "cpu")
        bos.reset()
        record.writeTo(bos)
        sender.writeValue(kafkaDataConfig.sourcetopic, bos.toByteArray)
        influxDBSink.write(record) match {
          case Right(_) => ()
          case Left(ex) => println(s"Write to Influx failed: ${ex.getMessage}")
        }
        pause(timeInterval)
      }
      println("Published another 100 records")
    }
  }

  def getModel() : ModelDescriptor = {
    val pByteArray = Files.readAllBytes(Paths.get(loaderConfig.model))
    val preprocessing = ModelPreprocessing(
      width = 3,
      mean = 0.34964777,
      std = 0.18103937,
      input = "input",
      output = "output"
    )
    ModelDescriptor(
      name = "Original_model",
      description = "Original model",
      dataType = "cpu",
      preprocessing = Some(preprocessing))
      .withData(ByteString.copyFrom(pByteArray))
  }

  private def pause(timeInterval: Duration): Unit = Thread.sleep(timeInterval.toMillis)
}
