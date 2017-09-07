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

  case class ConfigData(is: InfluxSettings) {
    def server = is.server
    def port = is.port
    def user = is.user
    def password = is.password
    def database = is.database
    def retentionPolicy = is.retentionPolicy
  }

  type ConfigReader[A] = ReaderT[Try, Config, A]

  private def fromInfluxConfig: ConfigReader[InfluxSettings] = Kleisli { (config: Config) =>
    Try {
      InfluxSettings(
        config.getString("influxdb.server"),
        config.getInt("influxdb.port"),
        config.getString("influxdb.user"),
        config.getString("influxdb.password"),
        config.getString("influxdb.database"),
        config.getString("influxdb.retentionPolicy")
      )
    }
  }

  def fromConfig: ConfigReader[ConfigData] = for {
    i <- fromInfluxConfig
  } yield ConfigData(i)
}

