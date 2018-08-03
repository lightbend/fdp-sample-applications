package com.lightbend.killrweather.loader.utils

import java.io.{ File, BufferedReader, InputStreamReader }
import java.util.zip.{ ZipEntry, ZipFile }
import scala.collection.JavaConverters._

object ZipFileIterator extends FileContentIterator {
  def apply(file: File, encoding: String) = new ZipFileIterator(file, encoding)
}

class ZipFileIterator(file: File, encoding: String) extends Iterator[String] {
  val zipFile = new ZipFile(file)
  val iterator: Iterator[String] = zipFile.entries().asScala.map(entryIterator).reduce(_ ++ _)

  override def hasNext: Boolean = iterator.hasNext

  override def next: String = iterator.next()

  private def entryIterator(entry: ZipEntry): Iterator[String] = {
    new BufferedReaderIterator(
      new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry), encoding))
    )
  }

}
