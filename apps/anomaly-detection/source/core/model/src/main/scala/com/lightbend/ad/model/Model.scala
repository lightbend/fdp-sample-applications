package com.lightbend.ad.model

import com.lightbend.model.cpudata.CPUData

/**
 * Created by boris on 5/9/17.
 * Basic trait for models. For simplicity, we assume the data to be scored are WineRecords.
 */
trait Model {
  def score(record: CPUData): Option[Int]
  def cleanup(): Unit
  def toBytes(): Array[Byte]
}
