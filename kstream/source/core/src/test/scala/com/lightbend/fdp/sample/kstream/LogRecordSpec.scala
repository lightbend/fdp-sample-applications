package com.lightbend.fdp.sample.kstream

import org.specs2.Specification

import cats._
import cats.data._
import cats.implicits._

import models._

class LogRecordSpec extends Specification { def is = s2"""

  This is a specification for LogRecord

  LogRecord should
    be parseable from log String                $e1
                                                """

  def e1 = {
    LogRecordData.records.map(LogParseUtil.parseLine(_)).sequenceU.get must haveSize(LogRecordData.records.size)
  }
}

object LogRecordData {
  val records: Vector[String] = Vector(
    s"""204.249.225.59 - - [28/Aug/1995:00:00:34 -0400] "GET /pub/rmharris/catalogs/dawsocat/intro.html HTTP/1.0" 200 3542""",
    s"""access9.accsyst.com - - [28/Aug/1995:00:00:35 -0400] "GET /pub/robert/past99.gif HTTP/1.0" 200 4993""",
    s"""access9.accsyst.com - - [28/Aug/1995:00:00:35 -0400] "GET /pub/robert/curr99.gif HTTP/1.0" 200 5836""",
    s"""world.std.com - - [28/Aug/1995:00:00:36 -0400] "GET /pub/atomicbk/catalog/sleazbk.html HTTP/1.0" 200 18338""",
    s"""cssu24.cs.ust.hk - - [28/Aug/1995:00:00:36 -0400] "GET /pub/job/vk/view17.jpg HTTP/1.0" 200 5944"""
  )
}
