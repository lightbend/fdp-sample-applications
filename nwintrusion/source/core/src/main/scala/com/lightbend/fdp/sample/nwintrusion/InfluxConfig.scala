package com.lightbend.fdp.sample.nwintrusion

import cats._
import cats.data._
import cats.instances.all._

import scala.util.Try
import com.typesafe.config.Config

object InfluxConfig {

  private[InfluxConfig] case class InfluxSettings(
    server: String, 
    port: Int, 
    user: String, 
    password: String, 
    database: String,
    retentionPolicy: String
  )

  private[InfluxConfig] case class GrafanaSettings(
    server: String, 
    port: Int, 
    user: String, 
    password: String
  )

  case class ConfigData(is: InfluxSettings, gs: GrafanaSettings) {
    def server = is.server
    def port = is.port
    def user = is.user
    def password = is.password
    def database = is.database
    def retentionPolicy = is.retentionPolicy
    def grafanaServer = gs.server
    def grafanaPort = gs.port
    def grafanaUser = gs.user
    def grafanaPassword = gs.password
  }

  type ConfigReader[A] = ReaderT[Try, Config, A]

  private def fromInfluxConfig: ConfigReader[InfluxSettings] = Kleisli { (config: Config) =>
    Try {
      InfluxSettings(
        config.getString("visualize.influxdb.server"),
        config.getInt("visualize.influxdb.port"),
        config.getString("visualize.influxdb.user"),
        config.getString("visualize.influxdb.password"),
        config.getString("visualize.influxdb.database"),
        config.getString("visualize.influxdb.retentionPolicy")
      )
    }
  }

  private def fromGrafanaConfig: ConfigReader[GrafanaSettings] = Kleisli { (config: Config) =>
    Try {
      GrafanaSettings(
        config.getString("visualize.grafana.server"),
        config.getInt("visualize.grafana.port"),
        config.getString("visualize.grafana.user"),
        config.getString("visualize.grafana.password")
      )
    }
  }

  def fromConfig: ConfigReader[ConfigData] = for {
    i <- fromInfluxConfig
    g <- fromGrafanaConfig
  } yield ConfigData(i, g)
}

