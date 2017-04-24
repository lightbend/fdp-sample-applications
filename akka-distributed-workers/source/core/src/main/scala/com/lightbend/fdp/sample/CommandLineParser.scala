package com.lightbend.fdp.sample

import scala.concurrent.duration._
import scopt.OptionParser

trait CommandLineParser {
  case class JobConfig(port: Int = 0, jobTimeout: Option[Duration] = None, jobScheduleFrequency: Option[Duration] = None,
    maxListSize: Option[Int] = None, maxListElementValue: Option[Int] = None)

  def parseCommandLineArgs(args: Array[String]): Option[JobConfig] = {
    val parser = new OptionParser[JobConfig]("akka-distributed-workers") {

      opt[Int]('p', "port")
        .required()
        .action { (value, config) => config.copy(port = value) }
        .text("port has to be an integer")

      opt[Duration]('t', "job-timeout")
        .action { (value, config) => config.copy(jobTimeout = Some(value)) }
        .text("jobTimeout has to be a valid scala.concurrent.Duration, e.g. 20s")

      opt[Duration]('f', "job-schedule-frequency")
        .action { (value, config) => config.copy(jobScheduleFrequency = Some(value)) }
        .text("jobScheduleFrequency has to be a valid scala.concurrent.Duration, e.g. 200 millis")

      opt[Int]('l', "max-list-size")
        .action { (value, config) => config.copy(maxListSize = Some(value)) }
        .text("maxListSize has to be an integer, e.g. 40")

      opt[Int]('v', "max-list-element-value")
        .action { (value, config) => config.copy(maxListElementValue = Some(value)) }
        .text("maxListElementValue has to be an integer, e.g. 500")
    }
    parser.parse(args, JobConfig()) 
  }
}
