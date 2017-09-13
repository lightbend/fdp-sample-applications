package com.lightbend.fdp.sample.nwintrusion

import org.apache.spark.mllib.linalg.{ Vectors, Vector }
import org.apache.spark.mllib.clustering.{ StreamingKMeans, StreamingKMeansModel }
import org.apache.spark.mllib.feature.StandardScaler
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.StreamingContext


object InfluxPublisher {
  def publishAnomaly(labeledData: DStream[(String, Vector)], model: StreamingKMeans,
    streamingContext: StreamingContext, c: InfluxConfig.ConfigData) = {

    println(s"Config = $c")
    val influxDBSink = streamingContext.sparkContext.broadcast(InfluxDBSink(c))
    writeToInflux(labeledData, model.latestModel, influxDBSink.value)
    // artificial delay inserted to simulate real life streaming
    Thread.sleep(1)
  }

  private def writeToInflux(labeledData: DStream[(String, Vector)], skmodel: StreamingKMeansModel,
    sink: InfluxDBSink) = labeledData.foreachRDD { rdd =>

    var currentHundredthFarthest: Double = 0.0d
    if (rdd.count() > 0) {
  
      /**
       * We compute the hundredth farthest point from centroid in each micro batch and
       * declare a data point anomalous if it's distance from the nearest centroid exceeds this value.
       * We maintain the highest of the values that we get for each batch. This is not entirely
       * foolproof a model, but this will improve with more iterations
       */ 
      val hundredthFarthestDistanceToCentroid: Double = { 
        val t100 = rdd.map { case (l, v) => distanceToCentroid(skmodel, v) }.top(100)
        if (t100.isEmpty) 0.00 else t100.last
      }
      if (hundredthFarthestDistanceToCentroid > currentHundredthFarthest) 
        currentHundredthFarthest = hundredthFarthestDistanceToCentroid

      (rdd.zipWithIndex).foreach { case ((label, vec), idx) =>
  
        // for each Vector in the RDD get the cluster membership, centroid and distance to centroid
        val distanceToCentroidForVec = distanceToCentroid(skmodel, vec) 
  
        // anomalous if its distance is more than the hundredth farthest distance
        // This strategy is taken from the book Advanced Analytics with Spark (http://shop.oreilly.com/product/0636920035091.do)
        val isAnomalous = distanceToCentroidForVec > currentHundredthFarthest
  
        if (isAnomalous) sink.write(distanceToCentroidForVec)
      }
    }
  }

  private def distanceToCentroid(skmodel: StreamingKMeansModel, vec: Vector): Double = {
    val predictedCluster: Int = skmodel.predict(vec)
    val centroid: Vector = skmodel.clusterCenters(predictedCluster)
    Vectors.sqdist(centroid, vec)
  }
}

