package com.lightbend.killrweater.beam.processors

import java.util

import com.lightbend.killrweater.beam.coders.ScalaIntCoder
import org.apache.beam.sdk.coders.{Coder, ListCoder}
import org.apache.beam.sdk.state._
import org.apache.beam.sdk.transforms.DoFn
import org.apache.beam.sdk.transforms.DoFn.{ProcessElement, StateId}
import org.apache.beam.sdk.values.KV



class GroupIntoBatchesFn[K, InputT, OutputT](val inputKeyCoder: Coder[K], val inputValueCoder: Coder[InputT],
                                               getTrigger: InputT => Int, convertData : java.util.List[InputT] => OutputT)
  extends DoFn[KV[K, InputT], KV[K, OutputT]] {


  @StateId("batch") private val batchSpec = StateSpecs.map(inputKeyCoder, ListCoder.of(inputValueCoder))

  @StateId("triggerValue") private val triggerSpec = StateSpecs.map(inputKeyCoder, ScalaIntCoder.of)

  @ProcessElement
  def processElement(ctx: DoFn[KV[K, InputT], KV[K, OutputT]]#ProcessContext,
                     @StateId("batch") batch: MapState[K, java.util.List[InputT]],
                     @StateId("triggerValue") triggerState : MapState[K,Int]): Unit = {
    val newKey = ctx.element.getKey
    val newRecord = ctx.element.getValue
    val currentTrigger = getTrigger(newRecord)
    val trigger = triggerState.get(newKey).read() match {
      case tr if tr != null => tr
      case _ => {
        triggerState.put(newKey, currentTrigger)
        currentTrigger
      }
    }
    val currentbatch = batch.get(newKey).read() match {
      case cb if cb != null => cb
      case _ => {
        val cb = new util.LinkedList[InputT]()
        batch.put(newKey, cb)
        cb
      }
    }
    if((currentTrigger != trigger) && (currentbatch.size() > 0)){
      flushBatch(ctx, newKey, batch.get(newKey).read())
      triggerState.put(newKey, currentTrigger)
    }
    batch.get(newKey).read().add(newRecord)
  }

  private def flushBatch(ctx: DoFn[KV[K, InputT], KV[K, OutputT]]#ProcessContext, key: K, batch: java.util.List[InputT]): Unit = {
    ctx.output(KV.of(key, convertData(batch)))
    batch.clear()
  }
}