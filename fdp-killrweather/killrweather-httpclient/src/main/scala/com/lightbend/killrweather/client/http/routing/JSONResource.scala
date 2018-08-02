package com.lightbend.killrweather.client.http.routing

/**
 * Created by boris on 7/17/17.
 * reffer to https://danielasfregola.com/2016/02/07/how-to-build-a-rest-api-with-akka-http/
 * and
 *     https://github.com/DanielaSfregola/quiz-management-service/blob/master/akka-http-crud/src/main/scala/com/danielasfregola/quiz/management/routing/MyResource.scala
 */

import akka.http.scaladsl.marshalling.{ ToResponseMarshallable, ToResponseMarshaller }
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.{ Directives, Route }
import com.lightbend.killrweather.client.http.serializers.JsonSupport

import scala.concurrent.Future

// Trait with JSON serializer

trait JSONResource extends Directives with JsonSupport {

  def completeWithLocationHeader[T](resourceId: Future[Option[T]], ifDefinedStatus: Int, ifEmptyStatus: Int): Route =
    onSuccess(resourceId) {
      case Some(t) => completeWithLocationHeader(ifDefinedStatus, t)
      case None => complete((ifEmptyStatus, None))
    }

  def completeWithLocationHeader[T](status: Int, resourceId: T): Route =
    extractRequestContext { requestContext =>
      val request = requestContext.request
      val location = request.uri.copy(path = request.uri.path / resourceId.toString)
      respondWithHeader(Location(location)) {
        complete((status, None))
      }
    }

  def complete[T: ToResponseMarshaller](resource: Future[Option[T]]): Route =
    onSuccess(resource) {
      case Some(t) => complete(ToResponseMarshallable(t))
      case None => complete((404, None))
    }

  def complete(resource: Future[Unit]): Route = onSuccess(resource) { complete((204, None)) }

}