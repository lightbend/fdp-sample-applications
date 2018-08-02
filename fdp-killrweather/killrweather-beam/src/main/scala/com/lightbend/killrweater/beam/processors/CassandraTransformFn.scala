package com.lightbend.killrweater.beam.processors

import org.apache.beam.sdk.transforms.DoFn
import org.apache.beam.sdk.transforms.DoFn.ProcessElement
import org.apache.beam.sdk.values.KV

class CassandraTransformFn[InputT, OutputT](convertData : KV[String, InputT] => OutputT) extends DoFn[KV[String, InputT], OutputT]{

  @ProcessElement
  def processElement(ctx: DoFn[KV[String, InputT], OutputT]#ProcessContext): Unit = {
    ctx.output(convertData(ctx.element()))
  }
}
