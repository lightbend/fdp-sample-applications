package com.lightbend.killrweather.loader.utils

object FilesIterator {
  def apply(file: java.io.File, encoding: String) = new FilesIterator(file, encoding)
}

class FilesIterator(file: java.io.File, encoding: String) extends Iterator[String] {

  private var iterator = if (file.exists && file.isDirectory) {
    file.listFiles.foldLeft(Seq.empty[String].toIterator) {
      (s, v) =>
        v.getName match {
          case name if name.endsWith("csv.gz") => s ++ GzFileIterator(v, encoding)
          case name if name.endsWith("csv.zip") => s ++ ZipFileIterator(v, encoding)
          case name if name.endsWith("csv") => s ++ FileIterator(v, encoding)
          case n => s
        }
    }
  } else Seq.empty.toIterator

  override def hasNext: Boolean = {
    iterator.hasNext
  }

  override def next: String =
    iterator.next
}

