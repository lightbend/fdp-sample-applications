/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lightbend.killrweather.settings

import scala.util.Try
import com.datastax.driver.core.ConsistencyLevel
import com.datastax.spark.connector.cql.{ AuthConf, NoAuthConf, PasswordAuthConf }

/**
 * Application settings. First attempts to acquire from the deploy environment.
 * If not exists, then from -D java system properties, else a default config.
 *
 * Settings in the environment such as: SPARK_HA_MASTER=local[10] is picked up first.
 *
 * Settings from the command line in -D will override settings in the deploy environment.
 * For example: sbt -Dspark.master="local[12]" run
 *
 * If you have not yet used Typesafe Config before, you can pass in overrides like so:
 *
 * {{{
 *   new Settings(ConfigFactory.parseString("""
 *      spark.master = "some.ip"
 *   """))
 * }}}
 *
 * Any of these can also be overridden by your own application.conf.
 *
 * TODO: Revert back to using Typesafe Config as in the original implementation and as the comments here suggest!!
 */
final class WeatherSettings() extends Serializable {

  // val AppName: String = "KillrWeather"  // Don't use, as different apps use WeatherSettings

  val localAddress = "localhost" //InetAddress.getLocalHost.getHostAddress

  // If running Kafka on your local machine, try localhost:9092
  val kafkaBrokers = sys.props.get("kafka.brokers") match {
    case Some(kb) => kb
    case None => "broker.kafka.l4lb.thisdcos.directory:9092" // for DC/OS - only works in the cluster!
  }
  println(s"Using Kafka Brokers: $kafkaBrokers")

  val SparkCleanerTtl = (3600 * 2)

  val SparkStreamingBatchInterval = 5000L

  val SparkCheckpointDir = "./checkpoints/"

  // If running Cassandra on your local machine, try your local IP address (127.0.0.1 might work)
  val CassandraHosts = sys.props.get("cassandra.hosts") match {
    case Some(ch) => ch
    case None => "node.cassandra.l4lb.thisdcos.directory" // This will work only on cluster
  }
  println(s"Using Cassandra Hosts: $CassandraHosts")

  val CassandraAuthUsername: Option[String] = sys.props.get("spark.cassandra.auth.username")

  val CassandraAuthPassword: Option[String] = sys.props.get("spark.cassandra.auth.password")

  val CassandraAuth: AuthConf = {
    val credentials = for (
      username <- CassandraAuthUsername;
      password <- CassandraAuthPassword
    ) yield (username, password)

    credentials match {
      case Some((user, password)) => PasswordAuthConf(user, password)
      case None => NoAuthConf
    }
  }

  val CassandraRpcPort = 9160

  val CassandraNativePort = 9042

  /* Tuning */

  val CassandraKeepAlive = 1000

  val CassandraRetryCount = 10

  val CassandraConnectionReconnectDelayMin = 1000

  val CassandraConnectionReconnectDelayMax = 60000

  /* Reads */
  val CassandraReadPageRowSize = 1000

  val CassandraReadConsistencyLevel: ConsistencyLevel = ConsistencyLevel.valueOf(ConsistencyLevel.LOCAL_ONE.name)

  val CassandraReadSplitSize = 100000

  /* Writes */

  val CassandraWriteParallelismLevel = 5

  val CassandraWriteBatchSizeBytes = 64 * 1024

  private val CassandraWriteBatchSizeRows = "auto"

  val CassandraWriteBatchRowSize: Option[Int] = {
    val NumberPattern = "([0-9]+)".r
    CassandraWriteBatchSizeRows match {
      case "auto" => None
      case NumberPattern(x) => Some(x.toInt)
      case other =>
        throw new IllegalArgumentException(
          s"Invalid value for 'cassandra.output.batch.size.rows': $other. Number or 'auto' expected"
        )
    }
  }

  val CassandraWriteConsistencyLevel: ConsistencyLevel = ConsistencyLevel.valueOf(ConsistencyLevel.LOCAL_ONE.name)

  val CassandraDefaultMeasuredInsertsCount: Int = 128

  val KafkaGroupId = "killrweather.group"
  val KafkaTopicRaw = "killrweather.raw"

  val CassandraKeyspace = "isd_weather_data"

  val CassandraTableRaw = "raw_weather_data"

  val CassandraTableDailyTemp = "daily_aggregate_temperature"
  val CassandraTableDailyWind = "daily_aggregate_windspeed"
  val CassandraTableDailyPressure = "daily_aggregate_pressure"
  val CassandraTableDailyPrecip = "daily_aggregate_precip"

  val CassandraTableMonthlyTemp = "monthly_aggregate_temperature"
  val CassandraTableMonthlyWind = "monthly_aggregate_windspeed"
  val CassandraTableMonthlyPressure = "monthly_aggregate_pressure"
  val CassandraTableMonthlyPrecip = "monthly_aggregate_precip"

  val CassandraTableSky = "sky_condition_lookup"
  val CassandraTableStations = "weather_station"
  val DataLoadPath = "./data/load"
  val DataFileExtension = ".csv.gz"

  // InfluxDB - These settings are effectively ignored if --without-influxdb is used.
  val influxDBServer: String = "http://influx-db.marathon.l4lb.thisdcos.directory"
  val influxDBPort: Int = 8086
  val influxDBUser: String = "root"
  val influxDBPass: String = "root"
  val influxDBDatabase: String = "weather"
  val retentionPolicy: String = "default"

  /** Attempts to acquire from environment, then java system properties. */
  def withFallback[T](env: Try[T], key: String): Option[T] = env match {
    case null => None
    case value => value.toOption
  }
}

object WeatherSettings {

  val USE_INFLUXDB_KEY = "killrweather.useinfluxdb"
  val USE_INFLUXDB_DEFAULT_VALUE = true;

  def handleArgs(appName: String, args: Array[String]): Unit = {

    // TODO: If WeatherSettings is switched to use Typesafe Config, then some of these flags can be handled through application.conf.
    def showHelp(): Unit = {
      println(s"""
        usage: scala ... $appName [options]

        where the options are:
          -h | --help              Show this message and exit.
          --with-influxdb          Set the system property ${USE_INFLUXDB_KEY} to ${USE_INFLUXDB_DEFAULT_VALUE} (default)
          --without-influxdb       Set the system property ${USE_INFLUXDB_KEY} to ${!USE_INFLUXDB_DEFAULT_VALUE}
          --kafka-brokers kb       IP or domain name for Kafka brokers (default: broker.kafka.l4lb.thisdcos.directory:9092).
          --cassandra-hosts ch     IP or domain name for Cassandra hosts (default: node.cassandra.l4lb.thisdcos.directory).

        Some Spark options are handled (others might be added later, as needed):
          --master master          Set the Spark master (only really necessary for local execution)

        For the GRPC Ingester Client app:
          --grpc-host host[:port]  Host and optional port (default: localhost:50051)
        """)
    }

    // Parse the args and set system properties for the optional flags
    def parseArgs(args2: Seq[String]): Unit = args2 match {

      case ("-h" | "--help") +: tail =>
        showHelp()
        sys.exit(0)

      case "--master" +: master +: tail =>
        println(s"Using Spark master: $master")
        setProp("spark.master", master)
        parseArgs(tail)

      case "--with-influxdb" +: tail =>
        setProp(USE_INFLUXDB_KEY, USE_INFLUXDB_DEFAULT_VALUE.toString)
        parseArgs(tail)

      case "--without-influxdb" +: tail =>
        setProp(USE_INFLUXDB_KEY, (!USE_INFLUXDB_DEFAULT_VALUE).toString)
        parseArgs(tail)

      case "--kafka-brokers" +: kb +: tail =>
        setProp("kafka.brokers", kb)
        parseArgs(tail)

      case "--cassandra-hosts" +: ch +: tail =>
        setProp("cassandra.hosts", ch)
        parseArgs(tail)

      case "--grpc-host" +: gh +: tail =>
        gh.split(":", 2) match {
          case Array(host, port) =>
            setProp("grpc.ingester.client.host", host)
            setProp("grpc.ingester.client.port", port)
          case Array(host) =>
            setProp("grpc.ingester.client.host", host)
        }
        parseArgs(tail)

      case Nil => // end of arguments

      case head +: tail => parseArgs(tail) // "head" is ignored
    }

    def setProp(key: String, value: String): Unit = {
      sys.props.update(key, value)
      println(s"Setting $key property to $value")
    }

    parseArgs(args)
  }
}
