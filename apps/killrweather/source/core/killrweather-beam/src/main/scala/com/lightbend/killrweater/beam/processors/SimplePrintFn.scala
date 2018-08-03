package com.lightbend.killrweater.beam.processors

import org.apache.beam.sdk.transforms.DoFn
import org.apache.beam.sdk.transforms.DoFn.ProcessElement
import org.apache.beam.sdk.values.KV


class SimplePrintFn[T](msg : String) extends DoFn[KV[String, T], KV[String, T]]{

  @ProcessElement
  def processElement(ctx: DoFn[KV[String, T], KV[String, T]]#ProcessContext) : Unit = {
    println(s"$msg : key ${ctx.element.getKey} value ${ctx.element.getValue}")
    ctx.output(KV.of(ctx.element().getKey, ctx.element().getValue))
  }
}
