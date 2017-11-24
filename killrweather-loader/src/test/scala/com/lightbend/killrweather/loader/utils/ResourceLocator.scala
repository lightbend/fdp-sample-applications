package com.lightbend.killrweather.loader.utils

import java.io.File

object ResourceLocator {
  def locate(filename: String): File = {
    val classLoader = this.getClass.getClassLoader
    new File(classLoader.getResource(filename).getFile)
  }
}
