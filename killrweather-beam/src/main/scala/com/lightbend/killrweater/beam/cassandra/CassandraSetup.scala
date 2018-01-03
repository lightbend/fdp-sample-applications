package com.lightbend.killrweater.beam.cassandra

import com.datastax.driver.core.Session

import scala.collection.mutable.ListBuffer
import scala.io.Source

object CassandraSetup {

  def setup(session: Session, file: String = "/create-timeseries.cql"): Unit = {

    readFile(file).foreach(command => session.execute(command))
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
