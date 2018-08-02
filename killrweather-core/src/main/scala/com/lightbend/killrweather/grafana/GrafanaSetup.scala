package com.lightbend.killrweather.grafana

import com.lightbend.killrweather.settings.WeatherSettings

import scala.io.Source
import scalaj.http.Http

class GrafanaSetup(user: String = "admin", password: String = "admin") {

  def setGrafana(): Unit = {

    import GrafanaSetup._

    // Set data source
    val dt = getData(dsfile, true)
    var responce = Http(datasource).auth(user, password).postData(dt).header("content-type", "application/json").asString
    // set dashboard
    val dash = getData(dashfile, false)
    responce = Http(dashboard).auth(user, password).postData(dash).header("content-type", "application/json").asString
  }
}

object GrafanaSetup {

  val settings = WeatherSettings()

  val grafanaServer = settings.graphanaConfig.server
  val grafanaPort = settings.graphanaConfig.port
  val datasource = s"http://$grafanaServer:$grafanaPort/api/datasources"
  val dashboard = s"http://$grafanaServer:$grafanaPort/api/dashboards/db"

  val dsfile = "/grafana-source.json"
  val dashfile = "/grafana-dashboard.json"

  def getData(name: String, replace: Boolean): String = {
    val stream = getClass.getResourceAsStream(name)
    val lines = Source.fromInputStream(stream).getLines
    val data = if(replace) {
        lines.map{ s =>
          if (s.contains("\"url\""))  s""" "url":"${settings.influxConfig.server}:${settings.influxConfig.port}", """
          else  s
        }.mkString
      } else {
        lines.mkString
      }
    stream.close()
    data
  }
}