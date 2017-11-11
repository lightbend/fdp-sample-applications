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

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.spark.SparkConf

import scala.concurrent.duration.FiniteDuration

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
 */

case class KafkaConfig(brokers: String, topic: String, group: String)
case class StreamingConfig(batchInterval: FiniteDuration, checkpointDir: String)
case class CassandraConfig(
  keyspace: String,
  tableRaw: String,
  tableDailyTemp: String,
  tableDailyWind: String,
  tableDailyPressure: String,
  tableDailyPrecip: String,
  tableMonthlyTemp: String,
  tableMonthlyWind: String,
  tableMonthlyPressure: String,
  tableMonthlyPrecip: String,
  tableSky: String,
  tableStations: String
)

final class WeatherSettings extends Serializable {

  val config = ConfigFactory.load()

  val kafkaConfig = config.as[KafkaConfig]("kafka")

  def sparkConf(): SparkConf = {
    val sparkConfig = config.getConfig("spark")
      .atKey("spark")
      .entrySet()
      .asScala
      .map { entry => (entry.getKey, entry.getValue.unwrapped.toString) }
      .toMap
    val sparkConf = new SparkConf()
    sparkConfig.foldLeft(sparkConf.setMaster(sparkConfig("spark.master"))) {
      case (conf, (setting, value)) =>
        conf.set(setting, value)
    }
  }

  val streamingConfig = config.as[StreamingConfig]("streaming")

  val cassandraConfig = config.as[CassandraConfig]("app.cassandra")

  val data = config.getConfig("app.data")
  val DataLoadPath = data.getString("loadPath")
  val DataFileExtension = data.getString("fileExtension")

  val influxConfig = config.getConfig("influx")

  // InfluxDB - These settings are effectively ignored if --without-influxdb is used.
  val influxDBServer: String = influxConfig.getString("server")
  val influxDBPort: Int = influxConfig.getInt("port")
  val influxDBUser: String = influxConfig.getString("user")
  val influxDBPass: String = influxConfig.getString("password")

  val influxAppConfig = config.getConfig("app.influx")

  val influxDBDatabase: String = influxAppConfig.getString("database")
  val retentionPolicy: String = influxAppConfig.getString("retentionPolicy")
  val useInflux: Boolean = influxAppConfig.getBoolean("enabled")

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
