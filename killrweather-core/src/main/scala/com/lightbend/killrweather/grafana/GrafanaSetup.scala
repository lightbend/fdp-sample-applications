package com.lightbend.killrweather.grafana

import com.lightbend.killrweather.settings.WeatherSettings

import scala.collection.mutable.ListBuffer
import scala.io.Source
import scalaj.http.Http

class GrafanaSetup(user: String = "admin", password: String = "admin") {

  def setGrafana(): Unit = {

    import GrafanaSetup._

    // Set data source
    val dt = getData(dsfile, true)
    Http(datasource).auth(user, password).postData(dt).header("content-type", "application/json").asString
    // set dashboard
    val dash = getData(dashfile, false)
    val _ = Http(dashboard).auth(user, password).postData(dash).header("content-type", "application/json").asString
  }
}

object GrafanaSetup {
  val settings = new WeatherSettings()
  import settings._

  val datasource = s"http://$GrafanaServer:$GrafanaPort/api/datasources"
  val dashboard = s"http://$GrafanaServer:$GrafanaPort/api/dashboards/db"
  val dsfile = "/grafana-source.json"
  val dashfile = "/grafana-dashboard.json"

  def getData(name: String, replace: Boolean): String = {
    val stream = getClass.getResourceAsStream(name)
    val data = replace match {
      case true => {
        val strings = new ListBuffer[String]()
        Source.fromInputStream(stream).getLines.foreach(s => {
          if (s.contains("\"url\"")) strings += s""" "url":"$influxDBServer: $influxDBPort", """
          else strings += s
        })
        strings.mkString
      }
      case _ => Source.fromInputStream(stream).getLines.mkString
    }
    stream.close()
    data
  }

  def main(args: Array[String]): Unit = {

    val client = new GrafanaSetup()
    client.setGrafana()
  }
}