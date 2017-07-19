package com.lightbend.killrweather.client.http.resources

/**
  * Created by boris on 7/17/17.
  *
  * based on https://github.com/DanielaSfregola/quiz-management-service/blob/master/akka-http-crud/src/main/scala/com/danielasfregola/quiz/management/resources/QuestionResource.scala
  */

import akka.http.scaladsl.server.Route
import com.lightbend.killrweather.client.http.routing.JSONResource
import com.lightbend.killrweather.client.http.services.RequestService
import com.lightbend.killrweather.utils.RawWeatherData

trait WeatherReportResource extends JSONResource{

  def requestRoutes(requestService : RequestService) : Route = pathPrefix("weather") {
    pathEnd {
      post{
        entity(as[RawWeatherData]) { request =>
          complete(requestService.processRequest(request))
        }
      }
    }
  }
}
