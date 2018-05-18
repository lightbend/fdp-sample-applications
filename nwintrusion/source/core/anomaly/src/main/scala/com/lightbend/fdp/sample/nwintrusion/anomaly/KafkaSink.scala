package com.lightbend.fdp.sample.nwintrusion.anomaly

import java.io.Serializable

import org.apache.kafka.clients.producer.{ KafkaProducer, ProducerRecord, Callback, RecordMetadata }

class KafkaSink(createProducer: () => KafkaProducer[Nothing, String]) extends Serializable {

  lazy val producer = createProducer()

  def send(topic: String, value: String): Unit = {
    val pr = new ProducerRecord(topic, value)
    val cb = new Callback {
      override def onCompletion(metadata: RecordMetadata, ex: Exception) = 
        if (ex != null) ex.printStackTrace
    }

    producer.send(pr, cb)
    ()
  }
}

object KafkaSink {
  def apply(config: java.util.Map[String, Object]): KafkaSink = {
    val f = () => {
      val producer = new KafkaProducer[Nothing, String](config)

      sys.addShutdownHook {
        producer.close()
      }

      producer
    }
    new KafkaSink(f)
  }
}
