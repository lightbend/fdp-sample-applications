package com.lightbend.killrweater.beam.kafka

import java.util

import org.apache.beam.sdk.options.PipelineOptionsFactory

object JobConfiguration{
  /**
    * Helper method to set the Kafka props from the pipeline options.
    *
    * @param options KafkaOptions
    * @return Kafka props
    */
  def getKafkaSenderProps(options: KafkaOptions): util.Map[String, AnyRef] = {

    import org.apache.kafka.clients.producer.ProducerConfig._

    val props: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
    props.put(BOOTSTRAP_SERVERS_CONFIG, options.getBroker)
    props.put(ACKS_CONFIG, "all")
    props.put(RETRIES_CONFIG, "2")
    props.put(BATCH_SIZE_CONFIG, "1024")
    props.put(LINGER_MS_CONFIG, "1")
    props.put(BUFFER_MEMORY_CONFIG, "1024000")
    props
  }

  def getKafkaConsumerProps(options: KafkaOptions): util.Map[String, AnyRef] = {

    import org.apache.kafka.clients.consumer.ConsumerConfig._

    val props: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef]
    props.put(BOOTSTRAP_SERVERS_CONFIG, options.getBroker)
    props.put(GROUP_ID_CONFIG, options.getDataGroup)
    props.put(AUTO_OFFSET_RESET_CONFIG, "earliest")
    props.put(ENABLE_AUTO_COMMIT_CONFIG, "true")
    props.put(AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000")
    props.put(SESSION_TIMEOUT_MS_CONFIG, "10000")
    props.put(MAX_POLL_RECORDS_CONFIG, "10")
    props
  }

  /**
    * Initializes some options for the Flink runner.
    *
    * @param args The command line args
    * @return the pipeline
    */
  def initializePipeline(args: Array[String]): KafkaOptions = {
    val options: KafkaOptions = PipelineOptionsFactory.fromArgs(args:_*).as(classOf[KafkaOptions])
    // Set job name
    options.setJobName("BeamModelServer")
    options
  }
}