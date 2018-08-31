package com.lightbend.ad.model.speculative

/**
 * Created by boris on 5/8/17.
 */
final case class SpeculativeExecutionStats(
  name: String,
  decider : String,
  tmout: Long,
  since: Long = System.currentTimeMillis(),
  usage: Long = 0,
  duration: Double = 0.0,
  min: Long = 0,
  max: Long = 0) {

  def incrementUsage(execution: Long): SpeculativeExecutionStats = {
    copy(
      usage = usage + 1,
      duration = duration + execution,
      min = if (execution < min) execution else min,
      max = if (execution > max) execution else max
    )
  }

  def updateConfig(timeout : Long):  SpeculativeExecutionStats = copy(tmout = timeout)
}

object SpeculativeExecutionStats{

  val empty = SpeculativeExecutionStats("", "", 0l, 0l, 0l, .0, 0l, 0l)
}