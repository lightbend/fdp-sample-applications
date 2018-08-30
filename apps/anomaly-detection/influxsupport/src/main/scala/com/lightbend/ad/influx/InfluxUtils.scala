package com.lightbend.ad.influx

import java.io._
import java.util.function.Consumer
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import org.influxdb.{InfluxDB, InfluxDBFactory}
import org.influxdb.dto.{Query, QueryResult}

import collection.JavaConverters._

import cats.effect.IO

object InfluxUtils {

  final val QUERY_RESULT_CHUNK_SIZE = 100

  def connectToInflux(url: String, user: String, password: String): IO[InfluxDB] =
    IO(InfluxDBFactory.connect(url, user, password))

  def getDatabase(influxDB: InfluxDB, databaseName: String): IO[String] = {
    IO {
      val databasesQuery = new Query("SHOW DATABASES", "")

      // check if we have the database as per the config
      influxDB.query(databasesQuery)
        .getResults
        .get(0)
        .getSeries
        .get(0).getValues match {
          case databases if databases == null => 
            throw new Exception(s"Database $databaseName does not exist")
          case databases =>
            val names = databases.asScala.map(_.get(0).toString())
            names.find(_ == databaseName).getOrElse(throw new Exception(s"Database $databaseName does not exist"))
        }
    }
  }

  private def query(influxDB: InfluxDB, str: String, db: String = ""): IO[QueryResult] = IO {
    influxDB.query(new Query(str, db))
  }

  private def createDatabase(influxDB: InfluxDB, database: String, retentionPolicy : String) : IO[Unit] = for {

    _ <- query(influxDB, s"""CREATE DATABASE "$database" """)
    _ <- query(influxDB, s"""DROP RETENTION POLICY "autogen" ON "$database" """)
    _ <- query(influxDB, s"""CREATE RETENTION POLICY "$retentionPolicy" ON "$database" DURATION 1d REPLICATION 1 SHARD DURATION 4h  DEFAULT""")

  } yield (())

  private def setDatabaseAndParams(influxDB: InfluxDB, database: String, retentionPolicy: String): IO[Unit] = IO {
    influxDB.setDatabase(database)

    // Flush every 2000 Points, at least every 100ms
    influxDB.enableBatch(2000, 100, TimeUnit.MILLISECONDS)

    // set retention policy
    val _ = influxDB.setRetentionPolicy(retentionPolicy)
  }

  def setup(url: String, user: String, password: String, database: String, retentionPolicy: String) : IO[InfluxDB] = for {
    influxDB <- connectToInflux(url, user, password)
    db       <- getDatabase(influxDB, database).handleErrorWith { _ => createDatabase(influxDB, database, retentionPolicy) }
    _        <- setDatabaseAndParams(influxDB, database, retentionPolicy)
  } yield influxDB

  private def classToNumber(s: String): Int = 
    if (s.toLowerCase == "normal") 0 else 1

  case class QueryMetadata(lastTime: String, numberOfRecords: Int)

  def queryNGenerateCPUDataFile(file: String, influxDB: InfluxDB, database: String, lastTimestamp: String): IO[QueryMetadata] = {
    // some disciplined resource management with bracket
    IO(new BufferedWriter(new FileWriter(file))).bracket { out =>
      IO {
        out.write(s""""Time","CPU","Class"\n""")
        val timestampCheckString = if (!lastTimestamp.isEmpty) s" where time > '$lastTimestamp'" else ""
        val query = new Query(s"SELECT * FROM cpu_data $timestampCheckString", database)
        val queue = new LinkedBlockingQueue[QueryResult]()
        var i: Int = 0

        influxDB.query(query, QUERY_RESULT_CHUNK_SIZE, new Consumer[QueryResult]() {
          def accept(result: QueryResult): Unit = {
            val _ = queue.add(result)
            ()
          }
        })

        var result: QueryResult = null
        var lastTime = ""
        do {
          result = queue.poll(10, TimeUnit.SECONDS)
          if (result.getError != "DONE") {
            extractCPUInfo(result) match {
              case Left(err) => println(s"Exception processing query in database $database: $err")
              case Right((lTime, values)) => values.foreach { record =>
                i = i + 1
                println(s"$i,${record._2},${classToNumber(record._1)}\n")
                out.write(s"$i,${record._2},${classToNumber(record._1)}\n")
                out.flush()
                lastTime = lTime
              }
            }
          }
        } while (result.getError != "DONE") // yuck! this is how the consumer detects end of streaming
        println(s"Query fetched $i records")
        QueryMetadata(lastTime, i)
      }
    } { out => 
      IO {
        influxDB.close()
        out.close()
      }
    }
  }

  private def extractCPUInfo(qr: QueryResult): Either[String, (String, List[(String, Double)])] = {
    if (qr.hasError) Left(qr.getError)
    else {
      val results = qr.getResults.asScala.toList
      if ((results == null) || (results.isEmpty)) Left(s"Got null/empty result")
      else {
        val res = results.head
        if (res.hasError) Left(res.getError)
        else {
          val listOfSeries = res.getSeries
          if ((listOfSeries == null) || (listOfSeries.isEmpty)) Left(s"Got null/empty series for result $res")
          else {
            val series = listOfSeries.asScala.toList.head
            val values = series.getValues
            if ((values == null) || (values.isEmpty)) Left(s"Got null/empty values for series $series / result $res")
            else {
              val vs = values.asScala
              Right((vs.last.get(0).toString, vs.map { r =>
                (r.get(1).toString, r.get(2).toString.toDouble) 
              }.toList))
            }
          }
        }
      }
    }
  }
}
