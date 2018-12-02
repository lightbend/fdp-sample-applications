package com.lightbend.fdp.sample.flink.reader

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.lightbend.fdp.sample.flink.config.TaxiRideConfig.{ConfigData, fromConfig}
import com.lightbend.fdp.sample.flink.models.PredictedTime
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import scala.concurrent.duration._


import scala.util.{Failure, Success}

object ResultReader {


  // get config info
  val config: ConfigData = fromConfig(ConfigFactory.load()) match {
    case Success(c)  => c
    case Failure(ex) => throw new Exception(ex)
  }

  implicit val system = ActorSystem("ModelServing")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val askTimeout = Timeout(30.seconds)

  println(s"Results reader, using kafka brokers at ${config.brokers}, results from topic ${config.outTopic}")

  val dataConsumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer)
    .withBootstrapServers(config.brokers)
    .withGroupId("resultsgroup")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  def main(args: Array[String]): Unit = {

    Consumer.atMostOnceSource(dataConsumerSettings, Subscriptions.topics(config.outTopic))
      .map(record => PredictedTime.fromString(new String(record.value(),"UTF-8"))).collect { case Success(dataRecord) => dataRecord }
      .runForeach(result => println(s"Ride ${result.rideId}, predicted time ${result.predictedTimeInMins}"))
    ()
  }
}
