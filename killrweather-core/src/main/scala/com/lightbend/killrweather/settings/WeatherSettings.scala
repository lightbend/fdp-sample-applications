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

import com.typesafe.config.{ Config, ConfigFactory }

import scala.collection.JavaConverters._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.spark.SparkConf

import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success, Try }

import com.datastax.driver.core.ConsistencyLevel

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
object CassandraConfig {
  val WriteConsistencyLevel: ConsistencyLevel = ConsistencyLevel.valueOf(ConsistencyLevel.LOCAL_ONE.name)
  val DefaultMeasuredInsertsCount: Int = 128
}

case class CassandraServerConfig(host: String, port: Int = 9042)

case class InfluxDBConfig(server: String, port: Int, user: String, password: String, enabled: Boolean) {
  def url = s"$server:$port"
}
case class GrafanaConfig(server: String, port: Int)



case class InfluxTableConfig(database: String, retentionPolicy: String)
case class GRPCConfig(host: String, port: Int)


class WeatherSettings(val config: Config) extends Serializable {

  val kafkaConfig: KafkaConfig = config.as[KafkaConfig]("kafka")

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

  val influxConfig = config.as[InfluxDBConfig]("influx")

  val influxTableConfig = config.as[InfluxTableConfig]("app.influx")

  val grpcConfig = config.as[GRPCConfig]("grpc.ingester.client")

  val graphanaConfig = config.as[GrafanaConfig]("grafana")

  lazy val cassandraServerConfig = {
    val host = config.getString("spark.cassandra.connection.host")
    val port = config.getInt("spark.cassandra.connection.port")
    CassandraServerConfig(host, port)
  }

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case w: WeatherSettings =>
        w.config == this.config
      case _ => false
    }
  }

  override def hashCode(): Int = config.hashCode()

}

object WeatherSettings {

  val baseConfig = ConfigFactory.load()

  def apply(appName: String, args: Array[String]): WeatherSettings = {

    // TODO: If WeatherSettings is switched to use Typesafe Config, then some of these flags can be handled through application.conf.
    def showHelp(): Unit = {
      println(s"""
        usage: scala ... $appName [options]

        where the options are:
          -h | --help              Show this message and exit.
          --with-influxdb          Uses InfluxDB as data timeseries store (default)
          --without-influxdb       Do not use InfluxDB as timeseries store
          --kafka-brokers kb       IP or domain name for Kafka brokers (default: broker.kafka.l4lb.thisdcos.directory:9092).
          --cassandra-hosts ch     IP or domain name for Cassandra hosts (default: node.cassandra.l4lb.thisdcos.directory).

        Some Spark options are handled (others might be added later, as needed):
          --master master          Set the Spark master (only really necessary for local execution)

        For the GRPC Ingester Client app:
          --grpc-host host[:port]  Host and optional port (default: localhost:50051)
        """)
    }

    class HelpOptionException extends Exception("show help")

    // Parse the args and set system properties for the optional flags
    def parseArgs(args2: Seq[String]): Try[Map[String, String]] = args2 match {
      case ("-h" | "--help") +: _ =>
        Failure(new HelpOptionException)

      case "--master" +: master +: tail =>
        println(s"Using Spark master: $master")
        parseArgs(tail).map(m => m + ("spark.master" -> master))

      case "--with-influxdb" +: tail =>
        parseArgs(tail).map(m => m + ("influx.enabled" -> "true"))

      case "--without-influxdb" +: tail =>
        parseArgs(tail).map(m => m + ("influx.enabled" -> "false"))

      case "--kafka-brokers" +: kb +: tail =>
        parseArgs(tail).map(m => m + ("kafka.brokers" -> kb))

      case "--cassandra-hosts" +: ch +: tail =>
        parseArgs(tail).map(m => m + ("spark.cassandra.connection.host" -> ch))

      case "--grpc-host" +: gh +: tail =>
        parseArgs(tail).map(m => m ++ (Seq("grpc.ingester.client.host", "grpc.ingester.client.port")).zip(gh.split(":", 2)))

      case head +: tail => parseArgs(tail) // unknown "head" is ignored

      case Nil => Success(Map.empty[String, String])
    }

    val overrides = parseArgs(args).getOrElse {
      showHelp()
      sys.exit(1)
    }
    val config = ConfigFactory.parseMap(overrides.asJava).withFallback(baseConfig)
    new WeatherSettings(config)
  }

  def apply(): WeatherSettings = {
    new WeatherSettings(baseConfig)
  }

}
