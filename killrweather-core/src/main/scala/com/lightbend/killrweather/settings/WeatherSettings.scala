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
 * Any of these can also be overriden by your own application.conf.
 *
 */
final class WeatherSettings extends Serializable {

  val localAddress = "localhost" //InetAddress.getLocalHost.getHostAddress

  val kafkaBrokers = /*"localhost:9092"*/ "broker.confluent-kafka.l4lb.thisdcos.directory:9092" // for dc/os

  val SparkMaster = "local[*]"

  val SparkCleanerTtl = (3600 * 2)

  val SparkStreamingBatchInterval = 5000L

  val SparkCheckpointDir = "hdfs://name-1-node.hdfs.mesos:9001/cpt/"

  val CassandraHosts = /*localAddress*/ "node.cassandra.l4lb.thisdcos.directory" // This will work only on cluster

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

  val AppName = "KillrWeather"

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

  // InfluxDB
  val influxDBServer: String = "http://influxdb1.marathon.l4lb.thisdcos.directory"
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
