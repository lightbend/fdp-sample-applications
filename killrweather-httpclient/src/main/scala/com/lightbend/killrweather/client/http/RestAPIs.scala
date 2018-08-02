package com.lightbend.killrweather.client.http

/**
 * Created by boris on 7/17/17.
 *
 * based
 *   https://github.com/DanielaSfregola/quiz-management-service/blob/master/akka-http-crud/src/main/scala/com/danielasfregola/quiz/management/Main.scala
 */

import java.net.InetAddress

import akka.http.scaladsl.server.Route

import scala.concurrent.duration._
import akka.actor._
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.lightbend.killrweather.client.http.resources.WeatherReportResource
import com.lightbend.killrweather.client.http.services.RequestService

object RestAPIs extends WeatherReportResource {

  def main(args: Array[String]) {

    // Create embedded Kafka and topic
    //    EmbeddedSingleNodeKafkaCluster.start()
    //    EmbeddedSingleNodeKafkaCluster.createTopic(KafkaTopicRaw)
    val host = InetAddress.getLocalHost.getHostAddress
    val port = 5000

    implicit val system = ActorSystem("WeatherDataIngester")
    implicit val materializer = ActorMaterializer()

    implicit val executionContext = system.dispatcher
    implicit val timeout = Timeout(10 seconds)

    val routes: Route = requestRoutes(new RequestService)

    val _ = Http().bindAndHandle(routes, host, port) map
      { binding => println(s"REST interface bound to ${binding.localAddress}") } recover {
        case ex =>
          println(s"REST interface could not bind to $host:$port - ${ex.getMessage}")
      }
  }
}
