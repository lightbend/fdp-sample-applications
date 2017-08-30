package com.lightbend.killrweather.client.utils

import java.io.{BufferedReader, FileInputStream, InputStreamReader}
import java.util.zip.GZIPInputStream


object FileIterator {
  def apply(file: java.io.File, encoding: String) = {
    new BufferedReaderIterator(
      new BufferedReader(
        new InputStreamReader(
          new FileInputStream(file), encoding
        )
      )
    )
  }
}
