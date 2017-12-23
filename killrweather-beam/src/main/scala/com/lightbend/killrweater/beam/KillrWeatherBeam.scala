package com.lightbend.killrweater.beam

import java.util

import com.lightbend.kafka.KafkaLocalServer
import com.lightbend.killrweater.beam.coders.ScalaStringCoder
import com.lightbend.killrweater.beam.data.{DailyWeatherData, DataObjects, RawWeatherData}
import com.lightbend.killrweater.beam.kafka.JobConfiguration
import com.lightbend.killrweater.beam.processors.{ConvertDataRecordFn, GroupIntoBatchesFn, SimplePrintFn}
import org.apache.beam.sdk.Pipeline
import org.apache.beam.sdk.coders.{ByteArrayCoder, KvCoder, NullableCoder, SerializableCoder}
import org.apache.beam.sdk.io.kafka.KafkaIO
import org.apache.beam.sdk.transforms.ParDo

object KillrWeatherBeam {

  def main(args: Array[String]): Unit = {
    // Create and initialize pipeline
    val options = JobConfiguration.initializePipeline(args)

    // Create embedded Kafka and topic
    val kafka = KafkaLocalServer(true)
    kafka.start()
    kafka.createTopic(options.getKafkaDataTopic)

    // Initialize pipeline
    val p = Pipeline.create(options)

    // Coder to use for Kafka data - raw byte message
    val kafkaDataCoder = KvCoder.of(NullableCoder.of(ByteArrayCoder.of), ByteArrayCoder.of)

    // Data Stream - gets data records from Kafka topic
    // It has to use KV to work with state, which is always KV

    val raw = p
      .apply("data", KafkaIO.readBytes
        .withBootstrapServers(options.getBroker)
        .withTopics(util.Arrays.asList(options.getKafkaDataTopic))
        .updateConsumerProperties(JobConfiguration.getKafkaConsumerProps(options))
        .withoutMetadata).setCoder(kafkaDataCoder)
      .apply("Convert to Record", ParDo.of(new ConvertDataRecordFn))
      .apply("Print it", ParDo.of(new SimplePrintFn[RawWeatherData]("Raw data")))

    val daily = raw
      .apply("Calculate dayily", ParDo.of(new GroupIntoBatchesFn[String,RawWeatherData, DailyWeatherData]
        (ScalaStringCoder.of, SerializableCoder.of(classOf[RawWeatherData]),
          DataObjects.getRawTrigger, DataObjects.convertRawData)))
            .setCoder(KvCoder.of(ScalaStringCoder.of, SerializableCoder.of(classOf[DailyWeatherData])))
      .apply("Print it", ParDo.of(new SimplePrintFn[DailyWeatherData]("Daily data")))

    // Run the pipeline
    p.run
  }
}
