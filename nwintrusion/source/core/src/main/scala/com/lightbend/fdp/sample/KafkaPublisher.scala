package com.lightbend.fdp.sample

import org.apache.kafka.common.serialization.{ ByteArraySerializer, StringSerializer }

import org.apache.spark.mllib.linalg.{ Vectors, Vector }
import org.apache.spark.mllib.clustering.{ StreamingKMeans, StreamingKMeansModel }
import org.apache.spark.mllib.feature.StandardScaler
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.StreamingContext


object KafkaPublisher {
  /**
   * Predict cluster from `labeledData` and publish to Kafka. 
   *
   * The following elements are published separated by comma:
   *
   * a. predicted cluster number
   * b. centroid of the cluster
   * c. distance of the point from the centroid
   * d. the label of the point
   * e. if anomalous
   * f. the point vector as a comma separated string
   *
   * For every microbatch, a separate record is written to the same topic, which contains
   * the pattern : "Centroids:/<centroid1>/<centroid2>/..."
   */ 
  def publishClusterInfoToKafka(labeledData: DStream[(String, Vector)], model: StreamingKMeans,
    streamingContext: StreamingContext, clusterTopic: String, broker: String) = {

    val kafkaParams = new java.util.HashMap[String, Object]()
    kafkaParams.put("key.serializer", classOf[ByteArraySerializer])
    kafkaParams.put("value.serializer", classOf[StringSerializer])
    kafkaParams.put("bootstrap.servers", broker)

    val sink = KafkaSink(kafkaParams)
    val broadcastedSink = streamingContext.sparkContext.broadcast(sink)
    writeToKafka(labeledData, model.latestModel, broadcastedSink.value, clusterTopic)
  }

  private def writeToKafka(labeledData: DStream[(String, Vector)], skmodel: StreamingKMeansModel,
    sink: KafkaSink, clusterTopic: String) = labeledData.foreachRDD { rdd =>

    var currentHundredthFarthest: Double = 0.0d
    if (rdd.count() > 0) {
  
      // for the RDD of the microbatch, get the mean distance to centroid for all the Vectors
      val distancesToCentroid: RDD[(Int, Double)] = rdd.map { case (l, v) => 
        val (cluster, _, dist) = distanceToCentroid(skmodel, v)
        (cluster, dist)
      }

      /**
       * We compute the hundredth farthest point from centroid in each micro batch and
       * delcare a data point anomalous if it's distance from the nearest centroid exceeds this value.
       * We maintain the highest of the values that we get for each batch. This is not entirely
       * foolproof a model, but this will improve with more iterations
       */ 
      val hundredthFarthestDistanceToCentroid: Double = { 
        val t100 = rdd.map { case (l, v) => distanceToCentroid(skmodel, v)._3 }.top(100)
        if (t100.isEmpty) 0.00 else t100.last
      }
      if (hundredthFarthestDistanceToCentroid > currentHundredthFarthest) 
        currentHundredthFarthest = hundredthFarthestDistanceToCentroid

      (rdd.zipWithIndex).foreach { case ((label, vec), idx) =>
  
        // for each Vector in the RDD get the cluster membership, centroid and distance to centroid
        val (predictedCluster, centroid, distanceToCentroidForVec) = distanceToCentroid(skmodel, vec) 
  
        // anomalous if its distance is more than the hundredth farthest distance
        // This strategy is taken from the book Advanced Analytics with Spark (http://shop.oreilly.com/product/0636920035091.do)
        val isAnomalous = distanceToCentroidForVec > currentHundredthFarthest
  
        val message = 
          s"""$predictedCluster,$centroid,$distanceToCentroidForVec,$label,$isAnomalous,${vec.toArray.mkString(",")}"""
  
        // write the centroid record only for the first item in the microbatch
        if (idx == 0) sink.send(clusterTopic, createClusterCenterRecord(skmodel))
        sink.send(clusterTopic, message)
      }
    }
  }

  /**
   * Create a record consisting of cluster centroids separated by "/". The cluster centroids are in the 
   * order of the cluster numbers.
   */ 
  private def createClusterCenterRecord(skmodel: StreamingKMeansModel): String = {
    val centers: Array[Vector] = skmodel.clusterCenters
    centers.foldLeft(s"Centroids:") { (a, e) =>
      s"$a/${e.toArray.mkString(",")}"
    }
  }

  private def distanceToCentroid(skmodel: StreamingKMeansModel, vec: Vector): (Int, Vector, Double) = {
    val predictedCluster: Int = skmodel.predict(vec)
    val centroid: Vector = skmodel.clusterCenters(predictedCluster)
    (predictedCluster, centroid, Vectors.sqdist(centroid, vec))
  }

  private def normalize(rdd: RDD[(String, Vector)]): RDD[(String, Vector)] = {
    val labels: RDD[String] = rdd.map(_._1)
    val data: RDD[Vector] = rdd.map(_._2)
    labels.zip(new StandardScaler().fit(data).transform(data))
  }
}
