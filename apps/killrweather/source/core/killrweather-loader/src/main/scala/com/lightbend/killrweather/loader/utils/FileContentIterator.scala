package com.lightbend.killrweather.loader.utils

import java.io.File
import scala.io.Source

trait FileContentIterator {
  def apply(file: File, encoding: String = "UTF-8"): Iterator[String]
}

object TextFileIterator extends FileContentIterator {
  def apply(file: java.io.File, encoding: String): Iterator[String] = Source.fromFile(file.getPath, encoding).getLines()
}
