package com.lightbend.killrweater.beam.processors

import com.datastax.driver.core.{Cluster, PreparedStatement, Session}
import com.lightbend.killrweater.beam.data.RawWeatherData
import com.lightbend.killrweather.settings.WeatherSettings
import org.apache.beam.sdk.transforms.DoFn
import org.apache.beam.sdk.transforms.DoFn.{ProcessElement, Setup, Teardown}
import org.apache.beam.sdk.values.KV

// Leveraging https://www.instaclustr.com/hello-cassandra-java-client-example/

class WriteRawToCassandraFn(server : String, port : Int) extends DoFn[KV[String, RawWeatherData], Unit] {

  val MAXATTEMPTS = 3
  val killrSettings = WeatherSettings("KillrWeather", new Array[String](0))
  import killrSettings._

  var cluster : Cluster = null
  var session : Session = null
  var prepared : PreparedStatement = null

  @Setup
  def connect() : Unit = {
    var connected = false
    var attempts = 0
    while(!connected && (attempts < MAXATTEMPTS)) {
      try {
        cluster = Cluster.builder().addContactPoint(server).withPort(port).withoutMetrics().build()
        session = cluster.connect()
        prepared = session.prepare(s"insert into ${cassandraConfig.keyspace}.${cassandraConfig.tableRaw} " +
          "(wsid, year, month, day, hour, temperature, dewpoint, pressure, wind_direction, wind_speed, sky_condition, sky_condition_text, one_hour_precip, six_hour_precip) " +
          "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
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
  def processElement(ctx: DoFn[KV[String, RawWeatherData], Unit]#ProcessContext): Unit = {
    val record = ctx.element()
    try {
      session.execute(prepared.bind(
        s"'${record.getKey}'",
        record.getValue.year.asInstanceOf[Object],
        record.getValue.month.asInstanceOf[Object],
        record.getValue.day.asInstanceOf[Object],
        record.getValue.hour.asInstanceOf[Object],
        record.getValue.temperature.asInstanceOf[Object],
        record.getValue.dewpoint.asInstanceOf[Object],
        record.getValue.pressure.asInstanceOf[Object],
        record.getValue.windDirection.asInstanceOf[Object],
        record.getValue.windSpeed.asInstanceOf[Object],
        record.getValue.skyCondition.asInstanceOf[Object],
        s"'${record.getValue.skyConditionText}'",
        record.getValue.oneHourPrecip.asInstanceOf[Object],
        record.getValue.sixHourPrecip.asInstanceOf[Object]))
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
