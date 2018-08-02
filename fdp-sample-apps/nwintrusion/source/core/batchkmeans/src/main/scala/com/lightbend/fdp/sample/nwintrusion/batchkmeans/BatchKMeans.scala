package com.lightbend.fdp.sample.nwintrusion.batchkmeans

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

import com.typesafe.scalalogging.LazyLogging

/**
 * Run BatchKMeans to iterate over the data set and find the optimal number of clusters.
 * <p>
 * This will run on streaming data from the specified Kafka topic in microbatches of the specified duration.
 * It's recommended that the duration is long enough to have sufficient amount of data for cluster number optimization.
 * Every run of the microbatch will iterate through cluster numbers <from cluster count> to <to cluster count>
 * in steps of <increment> and record the mean squared error. e.g. if <from cluster count> = 10 and <to cluster count>
 * = 40 and <increment> = 10, then each microbatch will run for cluster numbers = [10, 20, 30, 40] and
 * record the mean squared error. We need to pick up the one after which the error starts to go up.
 **/
object BatchKMeans extends LazyLogging {

  case class CommandLineConfig(sparkMaster: Option[String] = None, topicToReadFrom: String = "", 
    kafkaBroker: String = "", microBatchInSeconds: Long = 0, fromClusterCount: Int = 0, toClusterCount: Int = 0, increment: Int = 0)

  def parseCLI(args: Array[String]): Option[CommandLineConfig] = {
    val parser = new scopt.OptionParser[CommandLineConfig]("batchkmeans") {

      opt[String]('s', "master").optional().action( (x, c) =>
        c.copy(sparkMaster = Some(x)) ).text("spark master: default local[*]")

      opt[String]('t', "read-topic").required().action( (x, c) =>
        c.copy(topicToReadFrom = x) ).text("kafka topic to read from")

      opt[String]('b', "kafka-broker").required().action( (x, c) =>
        c.copy(kafkaBroker = x) ).text("kafka broker")

      opt[Long]('m', "micro-batch-secs").required().action( (x, c) =>
        c.copy(microBatchInSeconds = x) ).text("micro-batch in seconds")

      opt[Int]('f', "from-cluster-count").required().action( (x, c) =>
        c.copy(fromClusterCount = x) ).text("k-means start number of clusters (k)")

      opt[Int]('c', "to-cluster-count").required().action( (x, c) =>
        c.copy(toClusterCount = x) ).text("k-means to number of clusters (k)")

      opt[Int]('i', "increment").required().action( (x, c) =>
        c.copy(increment = x) ).text("increment cluster count by")

      help("help").text("prints this usage text")
    }

    parser.parse(args, CommandLineConfig()) 
  }    

  def main(args: Array[String]): Unit = {

    val cliConfig = parseCLI(args).getOrElse(sys.error("Invalid command line arguments"))
    println(s"Starting batch k-means service with config: $cliConfig")
    logger.info(s"Starting batch k-means service with config: $cliConfig")

    val microbatchDuration = Seconds(cliConfig.microBatchInSeconds)
    val topicToReadFrom = Array(cliConfig.topicToReadFrom)
    val broker = cliConfig.kafkaBroker
    val fromClusterCount = cliConfig.fromClusterCount
    val toClusterCount = cliConfig.toClusterCount
    val increment = cliConfig.increment

    if (fromClusterCount > toClusterCount) sys.error(s"Invalid cluster count range provided [$fromClusterCount,$toClusterCount]")

    val sparkConf = cliConfig.sparkMaster.map(m => new SparkConf().setAppName(getClass.getName).setMaster(m))
      .getOrElse(new SparkConf().setAppName(getClass.getName))

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
          logger.info(s"No of clusters = $noOfClusters, score = $clusteringScore")
        }
      }
    }

    sys.ShutdownHookThread {
      streamingContext.stop(true, true)
    }

    streamingContext.start()
    streamingContext.awaitTermination()
  }

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

