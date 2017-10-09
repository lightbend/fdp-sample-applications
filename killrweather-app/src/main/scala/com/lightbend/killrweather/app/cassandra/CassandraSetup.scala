package com.lightbend.killrweather.app.cassandra

import com.datastax.driver.core.Cluster

import scala.io.Source

class CassandraSetup(server: String, port: Int = 9042) {

  val cluster = new Cluster.Builder()
    .addContactPoints(server)
    .withPort(port).withoutMetrics()
    .build()
  val session = cluster.connect()

  def executeCommand(command: String): Unit = {

    session.execute(command)
  }

  def close(): Unit = {
    session.close
    cluster.close
  }
}

object CassandraSetup {

  def main(args: Array[String]): Unit = {

    val client = new CassandraSetup("node-0-server.cassandra.autoip.dcos.thisdcos.directory")
    readFile("/create-timeseries.cql", client)
    client.close()
  }

  def readFile(name: String, client: CassandraSetup): Unit = {
    val command = StringBuilder.newBuilder
    for (line <- Source.fromInputStream(getClass.getResourceAsStream(name)).getLines) {
      if (command.length > 0 || line.toUpperCase().startsWith("CREATE") || line.toUpperCase.startsWith("USE")) {
        val code = line.split("//")
        command.append(code(0))
      }
      if (line.endsWith(";") && command.length > 0) {
        println(s"cql command ${command.toString()}")
        client.executeCommand(command.toString())
        command.clear()
      }
    }
  }
}