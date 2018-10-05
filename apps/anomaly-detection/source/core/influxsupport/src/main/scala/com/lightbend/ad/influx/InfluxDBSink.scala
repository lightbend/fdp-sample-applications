package com.lightbend.ad.influx

import java.util.concurrent.TimeUnit

import com.lightbend.ad.configuration.IntelConfig
import com.typesafe.config.ConfigFactory
import com.lightbend.model.cpudata.CPUData
import org.influxdb.InfluxDB
import org.influxdb.dto.Point

import com.google.common.collect.EvictingQueue
import com.google.common.collect.Queues

import scala.concurrent.duration._
import cats.effect._
import cats.syntax.all._

class InfluxDBSink(ioinflux: IO[InfluxDB]) extends Serializable {

  import InfluxDBSink._

  println(s"Setting InfluxDB sink. URL : ${config.url},  database : ${tableConfig.database}, user : ${config.user}, password : ${config.password}")

  // we either get an InfluxDB or not
  val maybeInflux: Option[InfluxDB] = ioinflux.map(Option(_)).handleErrorWith {error => 
    println(s"Connection to Influx failed: $error")
    val n: Option[InfluxDB] = None
    IO(n)
  }.unsafeRunSync

  // create a cyclic queue of 1 element
  // swap out if we need to replace the instance of InfluxDB
  val dbpool = Queues.synchronizedQueue(EvictingQueue.create[InfluxDB](1))

  // add to pool if we have one
  maybeInflux.foreach(dbpool.add(_))

  def write(cpu: CPUData): WriteStatus = {
    val cpuPoint = Point.measurement("cpu_data").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
    cpuPoint.addField("utilization", cpu.utilization)
    cpu._class match {
      case 0 => cpuPoint.tag("type", "normal")
      case _ => cpuPoint.tag("type", "anomaly")
    }

    write(cpuPoint.build()).handleErrorWith { error =>
      IO(Left(error))
    }.unsafeRunSync
  }

  def write(serving: ServingData): WriteStatus = {
    val servingPoint = Point.measurement("serving_data").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
      .addField("served", serving.served)
      .addField("source", serving.source)
      .addField("duration", serving.duration)
      .tag("model", serving.model)

    write(servingPoint.build()).handleErrorWith { error =>
      IO(Left(error))
    }.unsafeRunSync
  }

  def write(serving: ServingModelData): WriteStatus = {
    val servingPoint = Point.measurement("serving_model_data").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
      .addField("served", serving.served)
      .addField("duration", serving.duration)
      .tag("model", serving.model)

    write(servingPoint.build()).handleErrorWith { error =>
      IO(Left(error))
    }.unsafeRunSync
  }

  def write(point: Point): IO[WriteStatus] = {

    // try setup in case of an earlier failure
    def tryAgain(): IO[WriteStatus] = for {
      influx <- InfluxUtils.setup(config.url, config.user, config.password, tableConfig.database, tableConfig.retentionPolicy)
      _      =  dbpool.add(influx)
      _      <- write(point)
    } yield Right(1)

    // peek at head : returns null if not found
    val influxDB = dbpool.peek()

    // try again if no connection
    if (influxDB == null) tryAgain()
    else IO(influxDB.write(point)).map(_ => Right(1)).handleErrorWith {ex => 
      // remove the connection
      val _ = dbpool.poll()
      IO(Left(new Throwable(ex)))
    }
  }
}

object InfluxDBSink {

  type WriteStatus = Either[Throwable, Int]
  final val maxRetryCount = 10

  val settings = IntelConfig.fromConfig(ConfigFactory.load()).get
  import settings._

  val config = influxConfig
  val tableConfig = influxTableConfig

  private def withShutdownHook(influxDB: InfluxDB): IO[Unit] = IO {
    val _ = sys.addShutdownHook {
      influxDB.flush () 
      influxDB.close ()
    }
  }

  def apply(): InfluxDBSink = {
    // Lift the created InfluxDB into the IO monad here : nothing runs now
    val i: IO[InfluxDB] = for {
      influxDB <- InfluxUtils.setup(config.url, config.user, config.password, tableConfig.database, tableConfig.retentionPolicy)
      _        <- withShutdownHook(influxDB)
      _        <- new GrafanaSetup().setGrafana("/grafana-source.json", "/grafana-dashboard.json")

    } yield influxDB

    new InfluxDBSink(i)
  }
}
