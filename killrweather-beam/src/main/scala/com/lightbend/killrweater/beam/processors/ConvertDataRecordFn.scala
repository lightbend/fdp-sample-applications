package com.lightbend.killrweater.beam.processors

import com.lightbend.killrweather.WeatherClient.WeatherRecord
import org.apache.beam.sdk.transforms.DoFn
import org.apache.beam.sdk.transforms.DoFn.ProcessElement
import org.apache.beam.sdk.values.KV

import com.lightbend.killrweater.beam.data.RawWeatherData


class ConvertDataRecordFn extends DoFn[KV[Array[Byte], Array[Byte]], KV[String, RawWeatherData]]{

  @ProcessElement
  def processElement(ctx: DoFn[KV[Array[Byte], Array[Byte]], KV[String, RawWeatherData]]#ProcessContext) : Unit = {
    // Unmarshall record and convert it to BeamRecord
    try {
      val record = WeatherRecord.parseFrom(ctx.element.getValue)
      ctx.output(KV.of(record.wsid, RawWeatherData(record)))
    }
    catch {
      case t: Throwable =>
        println(s"Error parsing weather Record ${t.printStackTrace()}")
    }
  }
}
