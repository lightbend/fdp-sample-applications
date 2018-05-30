package com.lightbend.fdp.sample.nwintrusion.anomaly

import scala.collection.mutable.ListBuffer
import scala.io.Source
import scalaj.http.Http

class GrafanaSetup(config: InfluxConfig.ConfigData) {

  import config._

  final val datasource = s"http://$grafanaServer:$grafanaPort/api/datasources"
  final val dashboard = s"http://$grafanaServer:$grafanaPort/api/dashboards/db"
  final val dsfile = "/grafana-source.json"
  final val dashfile = "/grafana-dashboard.json"

  private def getData(name: String, replace: Boolean): String = {
    val stream = getClass.getResourceAsStream(name)
    val data = replace match {
      case true => {
        val strings = new ListBuffer[String]()
        Source.fromInputStream(stream).getLines.foreach(s => {
          if (s.contains("\"url\"")) strings += s""" "url":"$server:$port", """
          else strings += s
        })
        strings.mkString
      }
      case _ => Source.fromInputStream(stream).getLines.mkString
    }
    stream.close()
    data
  }

  def setGrafana(): Unit = try {
    // Set data source
    val dt = getData(dsfile, true)
    val st1 = Http(datasource).auth(grafanaUser, grafanaPassword).postData(dt).header("content-type", "application/json").asString
    println(s"datasource loading $datasource $st1")

    // set dashboard
    val dash = getData(dashfile, false)
    println(s"dashboard string $dash")
    val st2 = Http(dashboard).auth(grafanaUser, grafanaPassword).postData(dash).header("content-type", "application/json").asString
    println(s"dashboard loading $dashboard $st2")
  } catch { case t: Throwable => t.printStackTrace } 
}
