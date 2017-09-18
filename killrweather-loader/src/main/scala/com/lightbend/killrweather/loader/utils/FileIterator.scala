package com.lightbend.killrweather.loader.utils

import java.io.{ BufferedReader, FileInputStream, InputStreamReader }

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
