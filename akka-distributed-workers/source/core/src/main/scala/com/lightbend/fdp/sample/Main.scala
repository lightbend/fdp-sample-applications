package com.lightbend.fdp.sample

import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.RootActorPath
import akka.actor.AddressFromURIString
import akka.actor.ActorPath
import akka.actor.Identify
import akka.actor.ActorIdentity
import akka.cluster.client.{ClusterClientReceptionist, ClusterClientSettings, ClusterClient}
import akka.cluster.singleton.{ClusterSingletonManagerSettings, ClusterSingletonManager}
import akka.japi.Util.immutableSeq
import akka.util.Timeout
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

object Main extends CommandLineParser {

  /**
   * Here's how the configuration works:
   * Consider the case of a setting of `job-timeout`:
   *
   * $ sbt "runMain com.lightbend.fdp.sample.Main --port 2551"
   * The value of `job-timeout` is picked from `application.conf`. Throws exception if not found.
   *
   * $ sbt "runMain com.lightbend.fdp.sample.Main --port 2551 --job-timeout 70s"
   * The value of `job-timeout` is picked from the command line argument.
   *
   * $ sbt -Dakka.job.job-timeout=50s  "runMain com.lightbend.fdp.sample.Main --port 2551"
   * The value of `job-timeout` from `application.conf` is overridden with the one supplied using `-D`.
   *
   * $ sbt -Dakka.job.job-timeout=50s  "runMain com.lightbend.fdp.sample.Main --port 2551 --job-timeout 70s"
   * The value of `job-timeout` is picked from the command line argument.
   *
   */ 
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      startBackend("backend", JobConfig(port = 2551))
      Thread.sleep(5000)
      startBackend("backend", JobConfig(port = 2552))
      startWorker(0)
      Thread.sleep(5000)
      startFrontend(JobConfig(port = 0))
    } else { 
      val config = parseCommandLineArgs(args) match {
        case Some(c) => c
        case None => throw new IllegalArgumentException("Invalid arguments passed")
      }
  
      val p = config.port

      if (2000 <= p && p <= 2999) startBackend("backend", config)
      else if (3000 <= p && p <= 3999) startFrontend(config)
      else startWorker(p)
    }
  }

  def startBackend(role: String, config: JobConfig): Unit = {

    implicit def asFiniteDuration(d: java.time.Duration) =
      scala.concurrent.duration.Duration.fromNanos(d.toNanos)
  
    val conf = 
      config.jobTimeout match {
        case Some(timeout) =>
          ConfigFactory.parseString(s"akka.cluster.roles=[$role]")
            .withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + config.port))
            .withFallback(ConfigFactory.parseString("akka.job.job-timeout=" + timeout))
            .withFallback(ConfigFactory.load())

        case None =>
          ConfigFactory.parseString(s"akka.cluster.roles=[$role]")
            .withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + config.port))
            .withFallback(ConfigFactory.load())
      }

    val system = ActorSystem("ClusterSystem", conf)
    println(s"""job-timeout = ${conf.getDuration("akka.job.job-timeout").getSeconds}""")

    system.actorOf(
      ClusterSingletonManager.props(
        Master.props(conf.getDuration("akka.job.job-timeout")),
        PoisonPill,
        ClusterSingletonManagerSettings(system).withRole(role)
      ),
      "master"
    )
  }

  def startFrontend(config: JobConfig): Unit = {

    def asFiniteDuration(d: java.time.Duration) =
      scala.concurrent.duration.Duration.fromNanos(d.toNanos)
  
    val cnf = 
      (config.maxListSize, config.maxListElementValue) match {
        case (Some(maxSize), Some(maxValue)) =>
          ConfigFactory.parseString("akka.remote.netty.tcp.port=" + config.port)
            .withFallback(ConfigFactory.parseString("akka.job.max-list-size=" + maxSize))
            .withFallback(ConfigFactory.parseString("akka.job.max-list-element-value=" + maxValue))
            .withFallback(ConfigFactory.load())

        case (Some(maxSize), None) =>
          ConfigFactory.parseString("akka.remote.netty.tcp.port=" + config.port)
            .withFallback(ConfigFactory.parseString("akka.job.max-list-size=" + maxSize))
            .withFallback(ConfigFactory.load())

        case (None, Some(maxValue)) =>
          ConfigFactory.parseString("akka.remote.netty.tcp.port=" + config.port)
            .withFallback(ConfigFactory.parseString("akka.job.max-list-element-value=" + maxValue))
            .withFallback(ConfigFactory.load())

        case (None, None) =>
          ConfigFactory.parseString("akka.remote.netty.tcp.port=" + config.port)
            .withFallback(ConfigFactory.load())
      }

    val conf = config.jobScheduleFrequency.map { f =>
      ConfigFactory.parseString("akka.job.job-schedule-frequency=" + f)
        .withFallback(cnf)
    }.getOrElse(cnf)

    println(s"""max-list-size = ${conf.getInt("akka.job.max-list-size")}""")
    println(s"""max-list-element-value = ${conf.getInt("akka.job.max-list-element-value")}""")
    println(s"""job-scheduling-frequency = ${conf.getDuration("akka.job.job-schedule-frequency").toMillis}""")

    val maxListSize = conf.getInt("akka.job.max-list-size")
    val maxElementValue = conf.getInt("akka.job.max-list-element-value")
    val jobScheduleFrequency = conf.getDuration("akka.job.job-schedule-frequency")

    val system = ActorSystem("ClusterSystem", conf)
    val frontend = system.actorOf(Props[Frontend], "frontend")

    system.actorOf(Props(classOf[WorkProducer], frontend, maxListSize, maxElementValue, 
      asFiniteDuration(jobScheduleFrequency)), "producer")
    system.actorOf(Props[WorkResultConsumer], "consumer")
  }

  def startWorker(port: Int): Unit = {
    val conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
      withFallback(ConfigFactory.load())
    val system = ActorSystem("WorkerSystem", conf)
    val initialContacts = immutableSeq(conf.getStringList("akka.worker.contact-points")).map {
      case AddressFromURIString(addr) ⇒ RootActorPath(addr) / "system" / "receptionist"
    }.toSet

    val clusterClient = system.actorOf(
      ClusterClient.props(
        ClusterClientSettings(system)
          .withInitialContacts(initialContacts)),
      "clusterClient")

    system.actorOf(Worker.props(clusterClient, Props[WorkExecutor]), "worker")
  }
}
