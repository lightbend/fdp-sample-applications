package com.lightbend.fdp.sample.kstream

import scopt.OptionParser

trait CommandLineParser {
  case class CliConfig(host: Option[String] = None, port: Int = 0)

  def parseCommandLineArgs(args: Array[String]): Option[CliConfig] = {
    val parser = new OptionParser[CliConfig]("kstream-log-processing") {

      opt[Int]('p', "port")
        .required()
        .action { (value, config) => config.copy(port = value) }
        .text("port has to be an integer")

      opt[String]('h', "host")
        .action { (value, config) => config.copy(host = Some(value)) }
        .text("Host name has to be a string")
    }
    parser.parse(args, CliConfig()) 
  }
}
