package com.lightbend.killrweather.app.grafana

import scalaj.http.Http
import scala.io.Source

class GrafanaSetup(port: Int = 3000, host: String = "grafana.marathon.l4lb.thisdcos.directory", user: String = "admin", password: String = "admin") {

  val datasource = s"http://$host:$port/api/datasources"
  val dashboard = s"http://$host:$port/api/dashboards/db"

  def setGrafana(): Unit = {

    import GrafanaSetup._

    // Set data source
    val dt = getData(dsfile)
    Http(datasource).auth(user, password).postData(dt).header("content-type", "application/json").asString
    // set dashboard
    val dash = getData(dashfile)
    Http(dashboard).auth(user, password).postData(dash).header("content-type", "application/json").asString
  }
}

object GrafanaSetup {
  val dsfile = "/grafana-source.json"
  val dashfile = "/grafana-dashboard.json"

  def getData(name: String): String = {
    val stream = getClass.getResourceAsStream(name)
    val data = Source.fromInputStream(stream).getLines.mkString
    stream.close()
    data
  }

  def main(args: Array[String]): Unit = {

    val client = new GrafanaSetup(27359)
    client.setGrafana()
  }
}