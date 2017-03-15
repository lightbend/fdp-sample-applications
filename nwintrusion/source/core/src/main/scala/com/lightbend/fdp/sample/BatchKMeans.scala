package com.lightbend.fdp.sample

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import org.apache.spark.mllib.linalg.{ Vector, Vectors }
import org.apache.spark.mllib.clustering.{ KMeans, KMeansModel }
import org.apache.spark.mllib.feature.StandardScaler
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkConf
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent

/**
 * Run BatchKMeans to iterate over the data set and find the optimal number of clusters.
 
 * Usage: BatchKMeans <topic to read from> <kafka broker> <micro batch duration in secs> <from cluster count> 
 *            <to cluster count> <increment>"
 *
 * This will run on streaming data from the specified Kafka topic in microbatches of the specified duration.
 * It's recommended that the duration is long enough to have sufficient amount of data for cluster number optimization.
 * Every run of the microbatch will iterate through cluster numbers <from cluster count> to <to cluster count>
 * in steps of <increment> and record the mean squared error. e.g. if <from cluster count> = 10 and <to cluster count>
 * = 40 and <increment> = 10, then each microbatch will run for cluster numbers = [10, 20, 30, 40] and
 * record the mean squared error. We need to pick up the one after which the error starts to go up.
 **/
object BatchKMeans {

  /**
   * args(0) - topic to read from
   * args(1) - kafka broker
   * args(2) - micro batch duration
   * args(3) - from cluster count
   * args(4) - to cluster count
   * args(5) - increment
   **/ 
  def main(args: Array[String]) {

    if (args.length != 6) sys.error(usage())

    val microbatchDuration = Seconds(args(2).toInt)
    val topicToReadFrom = Array(args(0))
    val broker = args(1)
    val fromClusterCount = args(3).toInt
    val toClusterCount = args(4).toInt
    val increment = args(5).toInt

    if (fromClusterCount > toClusterCount) sys.error(s"Invalid cluster count range provided [$fromClusterCount,$toClusterCount]")

    val sparkConf = new SparkConf().setAppName(getClass.getName)
    val streamingContext = new StreamingContext(sparkConf, microbatchDuration)
    streamingContext.checkpoint("checkpoint-batch")

    val kafkaParams = Map[String, Object](
      "bootstrap.servers"    -> broker,
      "key.deserializer"     -> classOf[ByteArrayDeserializer],
      "value.deserializer"   -> classOf[Tuple2StringSerde],
      "group.id"             -> "example",
      "auto.offset.reset"    -> "latest",
      "enable.auto.commit"   -> (false: java.lang.Boolean)
    )

    val stream: DStream[ConsumerRecord[Array[Byte], (String, String)]] = 
      KafkaUtils.createDirectStream[Array[Byte], (String, String)](
        streamingContext,
        PreferConsistent,
        Subscribe[Array[Byte], (String, String)](topicToReadFrom, kafkaParams)
      )

    val connectionData: DStream[Vector] = stream.map { record =>
      val (label, connectionInfo) = record.value
      Vectors.dense(connectionInfo.split(",").map(_.toDouble))
    }

    connectionData.foreachRDD { rdd =>
      if (!rdd.isEmpty()) {
        val normalizedData: RDD[Vector] = normalize(rdd).cache()
  
        (fromClusterCount to toClusterCount by increment).foreach { noOfClusters =>
          val trainedModel: KMeansModel = trainModel(normalizedData, noOfClusters)
          val clusteringScore: Double = normalizedData.map(distanceToCentroid(trainedModel, _)).mean()
          println(s"No of clusters = $noOfClusters, score = $clusteringScore")
        }
      }
    }

    sys.ShutdownHookThread {
      streamingContext.stop(true, true)
    }

    streamingContext.start()
    streamingContext.awaitTermination()
  }

  private def usage() = """
    |Run BatchKMeans to iterate over the data set and find the optimal number of clusters.
    |
    |Usage: BatchKMeans <topic to read from> <kafka broker> <micro batch duration in secs> <from cluster count> <to cluster count> <increment>"
    |
    |This will run on streaming data from the specified Kafka topic in microbatches of the specified duration.
    |It's recommended that the duration is long enough to have sufficient amount of data for cluster number optimization.
    |Every run of the microbatch will iterate through cluster numbers <from cluster count> to <to cluster count>
    |in steps of <increment> and record the mean squared error. e.g. if <from cluster count> = 10 and <to cluster count>
    |= 40 and <increment> = 10, then each microbatch will run for cluster numbers = [10, 20, 30, 40] and
    |record the mean squared error. We need to pick up the one after which the error starts to go up.
  """.stripMargin

  /**
   * Train a KMean model using normalized data.
   */
  private def trainModel(normalizedData: RDD[Vector], noOfClusters: Int): KMeansModel = {
    val kmeans = new KMeans()
    kmeans.setK(noOfClusters)
    kmeans.run(normalizedData)
  }

  private def normalize(rdd: RDD[Vector]): RDD[Vector] = {
    new StandardScaler().fit(rdd).transform(rdd)
  }

  private def distanceToCentroid(model: KMeansModel, vec: Vector): Double = {
    val predictedCluster: Int = model.predict(vec)
    val centroid: Vector = model.clusterCenters(predictedCluster)
    Vectors.sqdist(centroid, vec)
  }
}

