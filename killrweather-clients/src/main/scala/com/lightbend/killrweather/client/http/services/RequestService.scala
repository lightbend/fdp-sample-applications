package com.lightbend.killrweather.client.http.services

import java.io.ByteArrayOutputStream

import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.lightbend.killrweather.utils.RawWeatherData
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import akka.kafka.scaladsl.Producer
import com.lightbend.killrweather.settings.WeatherSettings
import com.lightbend.killrweather.Record.WeatherRecord

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by boris on 7/17/17.
  * based on
  *   https://github.com/DanielaSfregola/quiz-management-service/blob/master/akka-http-crud/src/main/scala/com/danielasfregola/quiz/management/services/QuestionService.scala
  */
class RequestService(implicit executionContext: ExecutionContext, materializer : ActorMaterializer, system : ActorSystem) {

  val settings = new WeatherSettings()
  import settings._
  import RequestService._

  val producerSettings = ProducerSettings(system, new ByteArraySerializer, new ByteArraySerializer)
    .withBootstrapServers("localhost:9092")

  def processRequest(report: RawWeatherData): Future[Unit] = Future {
//    Source.single(report).runWith(Sink.foreach(println))
    Source.single(report).map { r =>
      new ProducerRecord[Array[Byte], Array[Byte]](KafkaTopicRaw, convertRecord(r))
    }.runWith(Producer.plainSink(producerSettings))
  }
}

object RequestService{

  private val bos = new ByteArrayOutputStream()

  def apply(implicit executionContext: ExecutionContext, materializer : ActorMaterializer, system : ActorSystem): RequestService = new RequestService()

  def convertRecord(report: RawWeatherData) : Array[Byte] = {
    bos.reset
    WeatherRecord(report.wsid,report.year, report.month, report.day, report.hour, report.temperature,
      report.dewpoint, report.pressure, report.windDirection, report.windSpeed, report.skyCondition,
      report.skyConditionText, report.oneHourPrecip, report.sixHourPrecip).writeTo(bos)
    bos.toByteArray
  }
}