package com.lightbend.killrweather.loader.utils.test

import com.lightbend.killrweather.loader.utils.FilesIterator

object TestFilesIterator {

  val file = "data/load/"

  def main(args: Array[String]) {

    val iterator = FilesIterator(new java.io.File(file), "UTF-8")
    var nrec = 0

    while (iterator.hasNext) {
      nrec += 1
      iterator.next
    }
    println(nrec)
    val iterator1 = FilesIterator(new java.io.File(file), "UTF-8")
    nrec = 0
    iterator1.foreach(r =>
      nrec += 1)
    println(nrec)
  }
}
