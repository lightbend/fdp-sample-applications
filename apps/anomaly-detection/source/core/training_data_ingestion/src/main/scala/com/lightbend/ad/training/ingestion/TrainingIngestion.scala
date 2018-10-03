package com.lightbend.ad.training.ingestion

import cats.effect.IO
import com.typesafe.config.ConfigFactory

import com.lightbend.ad.influx.InfluxUtils._
import com.lightbend.ad.kafka._

import java.util.concurrent.TimeUnit
import java.time.{LocalDateTime, Instant}

import scala.concurrent._
import scala.concurrent.duration._
import monix.execution.Scheduler.{global => scheduler}

import IOUtils._
import com.lightbend.ad.configuration.IntelConfig
import IntelConfig._

object TrainingIngestion {

  def ingestAndWriteToFile(configData: IntelSettings): IO[Boolean] = {

    import configData._
    for {

      // connect to Influx with user and password
      influxDB        <- connectToInflux(influxConfig.url, influxConfig.user, influxConfig.password)

      // get the database name
      name            <- getDatabase(influxDB, influxTableConfig.database)

      // get last timestamp recorded, empty string if not found
      timestamp       <- getLastTimestamp(ingesterConfig.lastTimestampFileName).handleErrorWith { _ => IO("") }

      // query from Influx based on last timestamp, generate data file
      queryMetadata   <- queryNGenerateCPUDataFile(ingesterConfig.dataFileName, influxDB, name, timestamp)

      // generate data completion file for downstream signal if we have the min no of records fetched
      status          <- generateDataCompletionFile(ingesterConfig.generationCompleteFileName, ingesterConfig.ingestThresholdCount, 
                                                    queryMetadata.numberOfRecords)

      // update last timestamp if we have the min no of records fetched
      _               <- writeLastTimestamp(ingesterConfig.lastTimestampFileName, ingesterConfig.ingestThresholdCount, 
                                            queryMetadata.numberOfRecords, queryMetadata.lastTime)
    } yield status
  }

  def neverCompletes[T]: Future[T] = Promise[T].future

  def main(args: Array[String]): Unit = {

    val configData = IntelConfig.fromConfig(ConfigFactory.load()).get

    println(s"Starting ingestion service with config: $configData")

    // ingest
    val _ = scheduler.scheduleWithFixedDelay(
      1, configData.ingesterConfig.ingestInterval.toSeconds, TimeUnit.SECONDS,
      new Runnable {
        def run(): Unit = {
          val st = ingestAndWriteToFile(configData).unsafeRunSync
          if (st) println(s"Generation of data complete (${Instant.now()} / ${LocalDateTime.now()} .. check file ${configData.ingesterConfig.generationCompleteFileName}")
          else println(s"Not enough data to start training - dataset fetched less than minibatch size (${Instant.now()} / ${LocalDateTime.now()} .. ")
        }
      }
    )

    // now wait indefinitely till the Pod is shot from outside
    val x = neverCompletes[Int]
    val res = Await.result(x, Duration.Inf)
    ()
  }
}

