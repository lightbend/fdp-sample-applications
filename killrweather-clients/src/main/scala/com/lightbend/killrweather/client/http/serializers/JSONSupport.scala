package com.lightbend.killrweather.client.http.serializers

/**
  * Created by boris on 7/17/17.
  * based on
  *     https://github.com/DanielaSfregola/akka-tutorials/blob/master/akka-http-client/src/main/scala/com/danielasfregola/akka/tutorials/serializers/JsonSupport.scala
  */
import org.json4s.{DefaultFormats, Formats}
import org.json4s.native

trait JsonSupport extends Json4sSupport {

  implicit val serialization = native.Serialization

  implicit def json4sFormats: Formats = DefaultFormats
}