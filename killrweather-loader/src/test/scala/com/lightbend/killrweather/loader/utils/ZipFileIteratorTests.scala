package com.lightbend.killrweather.loader.utils

import org.scalatest.{ Matchers, WordSpec }
import ResourceLocator._

import scala.io.Source

class ZipFileIteratorTests extends WordSpec with Matchers {
  "ZipFileIterator" should {
    "iterate through the contents of a zip file" in {
      val zipFile = locate("samples/sample-1-2-csv.zip")
      val src1 = locate("samples/sample1.csv")
      val src2 = locate("samples/sample2.csv")

      val sources = (Source.fromFile(src1).getLines() ++ Source.fromFile(src2).getLines()).toList
      (sources.iterator zip ZipFileIterator(zipFile)).foreach { case (l1, l2) => l1 should be(l2) }
      ZipFileIterator(zipFile).size should be(sources.size)
    }
  }
}
