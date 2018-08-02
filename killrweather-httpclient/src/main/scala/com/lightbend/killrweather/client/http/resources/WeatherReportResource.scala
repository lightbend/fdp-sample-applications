package com.lightbend.killrweather.client.http.resources

/**
 * Created by boris on 7/17/17.
 *
 * based on https://github.com/DanielaSfregola/quiz-management-service/blob/master/akka-http-crud/src/main/scala/com/danielasfregola/quiz/management/resources/QuestionResource.scala
 */

import akka.http.scaladsl.server.Route
import com.lightbend.killrweather.utils.RawWeatherData
import akka.http.scaladsl.model.StatusCodes._
import com.lightbend.killrweather.client.http.routing.JSONResource
import com.lightbend.killrweather.client.http.services.RequestService

import scala.concurrent.ExecutionContext

trait WeatherReportResource extends JSONResource {

  def requestRoutes(requestService: RequestService)(implicit executionContext: ExecutionContext): Route = pathPrefix("weather") {
    pathEnd {
      post {
        entity(as[RawWeatherData]) { request =>
          complete(requestService.processRequest(request).map(_ => OK))
        }
      }
    }
  }
}
