package com.lightbend.ad.model

import java.io.ByteArrayOutputStream

import com.lightbend.model.cpudata.{CPUData, ServingResultMessage}

import scala.util.Try

/**
 * Created by boris on 5/8/17.
 */
object DataRecord {

  val bos = new ByteArrayOutputStream()

  def fromByteArray(message: Array[Byte]): Try[CPUData] = Try {
    CPUData.parseFrom(message)
  }

  def toByteArray(servingResult : ServingResultMessage) : Array[Byte] = {
    bos.reset()
    servingResult.writeTo(bos)
    bos.toByteArray
  }
}
