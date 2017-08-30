package com.lightbend.killrweather.client.utils

import java.io.{BufferedReader, InputStreamReader}
import java.util.zip.{ZipEntry, ZipFile}


object ZipFileIterator {
  def apply(file: java.io.File, encoding: String) = {
    new ZipFileIterator(file,encoding)
  }
}

class ZipFileIterator(file: java.io.File, encoding: String) extends Iterator[String]{

  val zfile = new ZipFile(file)
  val entries = zfile.entries()
  var entry : ZipEntry = null;
  var iterator : BufferedReaderIterator = null

  override def hasNext() : Boolean = {
    if(entry == null){
      iterator = createIterator()
    }
    if(iterator == null)
      false
     else
      iterator.hasNext()
  }

  override def next() : String = iterator.next()

  private def createIterator() : BufferedReaderIterator = {
    entries.hasMoreElements match {
      case true => {
        entry = entries.nextElement()
        new BufferedReaderIterator(
          new BufferedReader(
            new InputStreamReader(
              zfile.getInputStream(entry), encoding
            )
          )
        )
      }
      case _ => null
    }
  }
}
