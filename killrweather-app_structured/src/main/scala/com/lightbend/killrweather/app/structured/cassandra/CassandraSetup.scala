package com.lightbend.killrweather.app.structured.cassandra

import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.spark.sql.SparkSession

import scala.collection.mutable.ListBuffer
import scala.io.Source

class CassandraSetup(sparkSession: SparkSession) {

  val connector = CassandraConnector.apply(sparkSession.sparkContext.getConf)

  def setup(file: String = "/create-timeseries.cql"): Unit = {

    val commands = readFile(file)
    connector.withSessionDo { session =>
      commands.foreach(command => session.execute(command))
    }
  }

  def readFile(name: String): ListBuffer[String] = {
    val commands = new ListBuffer[String]
    val command = StringBuilder.newBuilder
    for (line <- Source.fromInputStream(getClass.getResourceAsStream(name)).getLines) {
      if (command.length > 0 || line.toUpperCase().startsWith("CREATE") || line.toUpperCase.startsWith("USE")) {
        val code = line.split("//")
        command.append(code(0))
      }
      if (line.endsWith(";") && command.length > 0) {
        commands.append(command.toString())
        command.clear()
      }
    }
    commands
  }
}