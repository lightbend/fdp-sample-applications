package com.lightbend.ad.influx

import com.lightbend.ad.configuration.IntelConfig
import com.typesafe.config.ConfigFactory

import scala.io.Source
import scalaj.http.Http
import cats.effect.IO

class GrafanaSetup() {

  def setGrafana(dsfile : String = "/grafana-source.json", dashfile : String = "/grafana-dashboard.json"): IO[Unit] = IO {

    import GrafanaSetup._

    println(s"Setting Grafana. URL : $gurl,  user : $user, password : $password")

    // Set data source
    val dt = getData(dsfile, true)
    var response = Http(datasource).auth(user, password).postData(dt).header("content-type", "application/json").asString
    println(s"Loaded datasourse $response")
    // set dashboard
    val dash = getData(dashfile, false)
    response = Http(dashboard).auth(user, password).postData(dash).header("content-type", "application/json").asString
    println(s"Loaded dashboard $response")
  }
}

object GrafanaSetup {

  val settings = IntelConfig.fromConfig(ConfigFactory.load()).get
  import settings._

  val gurl = grafanaConfig.url
  val datasource = s"$gurl/api/datasources"
  val dashboard = s"$gurl/api/dashboards/db"
  val user = grafanaConfig.user
  val password = grafanaConfig.password

  def getData(name: String, replace: Boolean): String = {
    val stream = getClass.getResourceAsStream(name)
    val lines = Source.fromInputStream(stream).getLines
    val data = if(replace) {
      lines.map{ s =>
        if (s.contains("\"url\""))  s""" "url":"${settings.influxConfig.url}", """
        else  s
      }.mkString
    } else {
      lines.mkString
    }
    stream.close()
    data
  }
}
