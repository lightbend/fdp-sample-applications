package com.lightbend.killrweather.loader.utils

import org.scalatest.{ Matchers, WordSpec }
import ResourceLocator._

import scala.io.Source

class TextFileIteratorTests extends WordSpec with Matchers {

  "TextFileIterator" should {
    "iterate through the contents of a file" in {
      val file = locate("samples/sample1.csv")
      val contents = Source.fromFile(file).getLines().toList

      (TextFileIterator(file) zip contents.iterator).foreach { case (t1, t2) => t1 should be(t2) }
      TextFileIterator(file).size should be(contents.size)
    }
  }

}
