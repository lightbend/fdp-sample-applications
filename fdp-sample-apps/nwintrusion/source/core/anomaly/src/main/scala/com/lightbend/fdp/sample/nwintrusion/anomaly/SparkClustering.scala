package com.lightbend.fdp.sample.nwintrusion.anomaly

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
import com.typesafe.scalalogging.LazyLogging
import scala.util.{ Success, Failure }
import InfluxConfig._

import scopt.OptionParser

object SparkClustering extends LazyLogging {

  case class CommandLineConfig(sparkMaster: Option[String] = None, withInfluxdb: Boolean = false, 
    topicToReadFrom: String = "", kafkaBroker: String = "", microBatchInSeconds: Long = 0, clusterCount: Int = 0)

  def parseCLI(args: Array[String]): Option[CommandLineConfig] = {
    val parser = new scopt.OptionParser[CommandLineConfig]("anomaly") {

      opt[String]('s', "master").optional().action( (x, c) =>
        c.copy(sparkMaster = Some(x)) ).text("spark master: default local[*]")

      opt[Unit]("with-influx").action( (_, c) =>
        c.copy(withInfluxdb = true) ).text("enables influx db and grafana support")

      opt[String]('t', "read-topic").required().action( (x, c) =>
        c.copy(topicToReadFrom = x) ).text("kafka topic to read from")

      opt[String]('b', "kafka-broker").required().action( (x, c) =>
        c.copy(kafkaBroker = x) ).text("kafka broker")

      opt[Long]('m', "micro-batch-secs").required().action( (x, c) =>
        c.copy(microBatchInSeconds = x) ).text("micro-batch in seconds")

      opt[Int]('k', "cluster-count").required().action( (x, c) =>
        c.copy(clusterCount = x) ).text("k-means number of clusters (k)")

      help("help").text("prints this usage text")
    }

    parser.parse(args, CommandLineConfig()) 
  }    

  def main(args: Array[String]): Unit = {

    val cliConfig = parseCLI(args).getOrElse(sys.error("Invalid command line arguments"))

    val conf = ConfigFactory.load()
    
    // get config info
    val config: Option[ConfigData] = fromConfig(conf) match {
      case Success(c)  => Some(c)
      case Failure(ex) => 
        ex.printStackTrace
        sys.error("Loading of configuration failed")
    }

    logger.info(s"Starting anomaly detection service with config: $config")

    val sparkConf = cliConfig.sparkMaster.map(m => new SparkConf().setAppName(getClass.getName).setMaster(m))
      .getOrElse(new SparkConf().setAppName(getClass.getName))

    val microbatchDuration = Seconds(cliConfig.microBatchInSeconds)
    val streamingContext = new StreamingContext(sparkConf, microbatchDuration)
    streamingContext.checkpoint("anomaly-checkpoint")

    val topicToReadFrom = Array(cliConfig.topicToReadFrom)
    val broker = cliConfig.kafkaBroker
    val noOfClusters = cliConfig.clusterCount

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

    // cluster distribution on prediction
    val predictedClusters: DStream[Int] = model.predictOn(data)

    // prints counts per cluster 
    predictedClusters.countByValue().print()

    sys.ShutdownHookThread {
      streamingContext.stop(true, true)
    }

    if (cliConfig.withInfluxdb) {
      config.foreach { c =>
        // publish anomaly to Influx
        InfluxPublisher.publishAnomaly(
          projected, model, streamingContext, c)
      }
    }

    streamingContext.start()
    streamingContext.awaitTermination()
  }

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
