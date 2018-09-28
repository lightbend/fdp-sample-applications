package com.lightbend.ad.training.ingestion

import java.io._
import java.nio.file.{Files, Paths}
import cats.effect.IO

object IOUtils {
  /**
   * case 1: all ok, we get back a BufferedWriter
   * case 2: file creation fails somehow and we have an exception in IO
   * case 3: threshold count check fails
   */ 
  def generateDataCompletionFile(name: String, ingestThresholdCount: Int, numberOfRecords: Int): IO[Boolean] = IO {
    if (numberOfRecords < ingestThresholdCount) false
    else {
      val _ = Files.newBufferedWriter(Paths.get(name), CHARSET) 
      true
    }
  }

  def writeLastTimestamp(file: String, ingestThresholdCount: Int, numberOfRecords: Int, lastTime: String): IO[Unit] = {
    if (numberOfRecords < ingestThresholdCount) IO(()) 
    else {
      IO(new BufferedWriter(new FileWriter(file))).bracket { out =>
        IO (out.write(lastTime))
      } { out => IO (out.close()) }
    }
  }

  def getLastTimestamp(file: String): IO[String] = IO(new BufferedReader(new FileReader(file))).bracket { in =>
    IO {
      val line = in.readLine()
      if (line == null) ""
      else line
    }
  } { in => IO (in.close()) }
}
