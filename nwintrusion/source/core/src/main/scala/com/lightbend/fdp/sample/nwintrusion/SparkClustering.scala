package com.lightbend.fdp.sample.nwintrusion

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import org.apache.spark.mllib.clustering.{ StreamingKMeans, StreamingKMeansModel }
import org.apache.spark.mllib.feature.PCA
import org.apache.spark.mllib.feature.StandardScaler
import org.apache.spark.mllib.linalg.{ Vector, Vectors }
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkConf
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka010._
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.{Seconds, StreamingContext}

import java.io.File
import com.typesafe.config.ConfigFactory
import scala.util.{ Success, Failure }
import InfluxConfig._

object SparkClustering {

  /**
   * argv(0) - the config file (influx.conf)
   * argv(1) - topic to read from
   * argv(2) - kafka broker
   * argv(3) - micro batch duration in seconds 
   * argv(4) - k 
   **/ 
  def main(args: Array[String]) {

    if (args.length != 5) sys.error(usage())

    // 
    // get config info
    val config: ConfigData = fromConfig(ConfigFactory.parseFile(new File(args(0))).resolve()) match {
      case Success(c)  => c
      case Failure(ex) => throw new Exception(ex)
    }

    val sparkConf = new SparkConf().setAppName(getClass.getName)
    val microbatchDuration = Seconds(args(3).toInt)
    val streamingContext = new StreamingContext(sparkConf, microbatchDuration)
    streamingContext.checkpoint("checkpoint")

    val topicToReadFrom = Array(args(1))
    val broker = args(2)
    val noOfClusters = args(4).toInt

    val kafkaParams = Map[String, Object](
      "bootstrap.servers"    -> broker,
      "key.deserializer"     -> classOf[ByteArrayDeserializer],
      "value.deserializer"   -> classOf[Tuple2StringSerde],
      "group.id"             -> s"example${System.currentTimeMillis}",
      "auto.offset.reset"    -> "latest",
      "enable.auto.commit"   -> (true: java.lang.Boolean)
    )

    // get the data from kafka
    val stream: DStream[ConsumerRecord[Array[Byte], (String, String)]] = 
      KafkaUtils.createDirectStream[Array[Byte], (String, String)](
        streamingContext,
        PreferConsistent,
        Subscribe[Array[Byte], (String, String)](topicToReadFrom, kafkaParams)
      )

    // label and vectorize the value
    val transformed: DStream[(String, Vector)] = stream.map { record =>
      val (label, value) = record.value
      val vector = Vectors.dense(value.split(",").map(_.toDouble))
      (label, vector)
    }

    transformed.cache()

    // normalize
    val normalized: DStream[(String, Vector)] = transformed.transform(normalize)

    // project to lower dimension
    val projected: DStream[(String, Vector)] = normalized.transform(projectToLowerDimension)

    // get data only
    val data: DStream[Vector] = projected.map(_._2)

    val decayFactor = 1      // assume stationary distribution
    val dimensions = 2       // our data is now a 2 dimension vector
    val weightPerCenter = 0.0

    val model = new StreamingKMeans()
      .setDecayFactor(decayFactor)
      .setK(noOfClusters)
      .setRandomCenters(dimensions, weightPerCenter)

    model.trainOn(data)

    val skmodel = model.latestModel

    skmodel.clusterCenters.foreach(println)
    skmodel.clusterWeights.foreach(println)

    // cluster distribution on prediction
    val predictedClusters: DStream[Int] = model.predictOn(data)

    // prints counts per cluster 
    predictedClusters.countByValue().print()

    predictedClusters.foreachRDD { rdd => println(rdd.count()) }

    sys.ShutdownHookThread {
      streamingContext.stop(true, true)
    }

    // publish anomaly to Influx
    InfluxPublisher.publishAnomaly(
      projected, model, streamingContext, config)

    streamingContext.start()
    streamingContext.awaitTermination()
  }

  private def usage() = """
    |Run SparkClustering to iterate over the data set in micro batches and generate clustering information with anomalies
    |
    |Usage: SparkClustering <topic to read from> <broker> <micro batch duration in secs> <k>
    |
    |This will run on streaming data from the specified Kafka topic in microbatches of the specified duration.
    |The result will tag every data point with a cluster number and also flag as anomaly for the anomalous data point.
    |The model for anomaly detection is based on the distance of the point from the nearest centroid being 3 standard
    |deviations away from the mean.
  """.stripMargin

  private def projectToLowerDimension: RDD[(String, Vector)] => RDD[(String, Vector)] = { rdd =>
    if (rdd.isEmpty) rdd else {
      // reduce to 2 dimensions
      val pca = new PCA(2).fit(rdd.map(_._2))

      // Project vectors to the linear space spanned by the top 2 principal
      // components, keeping the label
      rdd.map(p => (p._1, pca.transform(p._2)))
    }
  }

  private def normalize: RDD[(String, Vector)] => RDD[(String, Vector)] = { rdd =>
    if (rdd.isEmpty) rdd else {
      val labels: RDD[String] = rdd.map(_._1)
      val data: RDD[Vector] = rdd.map(_._2)
      labels.zip(new StandardScaler().fit(data).transform(data))
    }
  }
}
