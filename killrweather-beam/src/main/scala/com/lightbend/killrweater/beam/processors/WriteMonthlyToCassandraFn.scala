package com.lightbend.killrweater.beam.processors

import com.datastax.driver.core.{Cluster, PreparedStatement, Session}
import com.lightbend.killrweater.beam.data.MonthlyWeatherData
import com.lightbend.killrweather.settings.WeatherSettings
import org.apache.beam.sdk.transforms.DoFn
import org.apache.beam.sdk.transforms.DoFn.{ProcessElement, Setup, Teardown}
import org.apache.beam.sdk.values.KV

// Leveraging https://www.instaclustr.com/hello-cassandra-java-client-example/

class WriteMonthlyToCassandraFn(server : String, port : Int) extends DoFn[KV[String, MonthlyWeatherData], Unit] {

  val MAXATTEMPTS = 3
  val settings = new WeatherSettings()
  import settings._

  var cluster : Cluster = null
  var session : Session = null
  var preparedTemp : PreparedStatement = null
  var preparedWind : PreparedStatement = null
  var preparedPressure : PreparedStatement = null
  var preparedPrecip : PreparedStatement = null

  @Setup
  def connect() : Unit = {
    var connected = false
    var attempts = 0
    while(!connected && (attempts < MAXATTEMPTS)) {
      try {
    cluster = Cluster.builder().addContactPoint(server).withPort(port).withoutMetrics().build()
    session = cluster.connect()
    preparedTemp = session.prepare(s"insert into $CassandraKeyspace.$CassandraTableMonthlyTemp " +
      "(wsid, year, month, high, low, mean, variance, stdev) " +
      "values (?, ?, ?, ?, ?, ?, ?, ?)")
    preparedWind = session.prepare(s"insert into $CassandraKeyspace.$CassandraTableMonthlyWind " +
      "(wsid, year, month, high, low, mean, variance, stdev) " +
      "values (?, ?, ?, ?, ?, ?, ?, ?)")
    preparedPressure = session.prepare(s"insert into $CassandraKeyspace.$CassandraTableMonthlyPressure " +
      "(wsid, year, month, high, low, mean, variance, stdev) " +
      "values (?, ?, ?, ?, ?, ?, ?, ?)")
    preparedPrecip = session.prepare(s"insert into $CassandraKeyspace.$CassandraTableMonthlyPrecip " +
      "(wsid, year, month, high, low, mean, variance, stdev) " +
      "values (?, ?, ?, ?, ?, ?, ?, ?)")

    connected = true
      }
      catch {
        case t: Throwable => {
          println(s"Exception connecting to Cassandra $t")
          teardown()
          Thread.sleep(100)
          attempts = attempts + 1
        }
      }
    }
  }


  @Teardown
  def teardown() : Unit = {
    try{
      if(session != null)session.close()
    }
    catch { case t: Throwable => }
    try {
      if(cluster != null)cluster.close()
    }
    catch { case t: Throwable => }
    session = null
    cluster = null
  }

  @ProcessElement
  def processElement(ctx: DoFn[KV[String, MonthlyWeatherData], Unit]#ProcessContext): Unit = {
    val record = ctx.element()
    try{
      session.execute(preparedTemp.bind(
        s"'${record.getKey}'",
        record.getValue.year.asInstanceOf[Object],
        record.getValue.month.asInstanceOf[Object],
        record.getValue.highTemp.asInstanceOf[Object],
        record.getValue.lowTemp.asInstanceOf[Object],
        record.getValue.meanTemp.asInstanceOf[Object],
        record.getValue.stdevTemp.asInstanceOf[Object],
        record.getValue.varianceTemp.asInstanceOf[Object]))
      session.execute(preparedWind.bind(
        s"'${record.getKey}'",
        record.getValue.year.asInstanceOf[Object],
        record.getValue.month.asInstanceOf[Object],
        record.getValue.highWind.asInstanceOf[Object],
        record.getValue.lowWind.asInstanceOf[Object],
        record.getValue.meanWind.asInstanceOf[Object],
        record.getValue.stdevWind.asInstanceOf[Object],
        record.getValue.varianceWind.asInstanceOf[Object]))
      session.execute(preparedPressure.bind(
        s"'${record.getKey}'",
        record.getValue.year.asInstanceOf[Object],
        record.getValue.month.asInstanceOf[Object],
        record.getValue.highPressure.asInstanceOf[Object],
        record.getValue.lowPressure.asInstanceOf[Object],
        record.getValue.meanPressure.asInstanceOf[Object],
        record.getValue.stdevPressure.asInstanceOf[Object],
        record.getValue.variancePressure.asInstanceOf[Object]))
      session.execute(preparedPrecip.bind(
        s"'${record.getKey}'",
        record.getValue.year.asInstanceOf[Object],
        record.getValue.month.asInstanceOf[Object],
        record.getValue.highPrecip.asInstanceOf[Object],
        record.getValue.lowPrecip.asInstanceOf[Object],
        record.getValue.meanPrecip.asInstanceOf[Object],
        record.getValue.stdevPrecip.asInstanceOf[Object],
        record.getValue.variancePrecip.asInstanceOf[Object]))
    }
    catch {
      case t: Throwable => {
        println(s"Exception writing to Cassandra $t")
        teardown()
        connect()
      }
    }
  }
}