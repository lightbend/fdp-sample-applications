package com.lightbend.fdp.sample.flink.app.model

import org.apache.commons.math3.stat.regression.SimpleRegression

/**
  * TravelTimePredictionModel provides a very simple regression model to predict the travel time
  * to a destination location depending on the direction and distance of the departure location.
  *
  * The model builds for multiple direction intervals (think of it as north, north-east, east, etc.)
  * a linear regression model (Apache Commons Math, SimpleRegression) to predict the travel time based
  * on the distance.
  *
  * NOTE: This model is not mean for accurate predictions but rather to illustrate Flink's handling
  * of operator state.
  *
  */
object TravelTimePredictionModel {
  val NUM_DIRECTION_BUCKETS = 8
  val BUCKET_ANGLE = 360 / NUM_DIRECTION_BUCKETS
}

class TravelTimePredictionModel() {

  import TravelTimePredictionModel._

  val models = new Array[SimpleRegression](NUM_DIRECTION_BUCKETS)
  for (i <- 0 to (NUM_DIRECTION_BUCKETS -1))
    models(i) = new SimpleRegression(false)

  /**
    * Predicts the time of a taxi to arrive from a certain direction and Euclidean distance.
    *
    * @param direction The direction from which the taxi arrives.
    * @param distance  The Euclidean distance that the taxi has to drive.
    * @return A prediction of the time that the taxi will be traveling or -1 if no prediction is
    *         possible, yet.
    */
  def predictTravelTime(direction: Int, distance: Double): Int = {
    models(getDirectionBucket(direction)).predict(distance) match {
      case prediction if(prediction.isNaN) => -1
      case prediction => prediction.toInt
    }
  }

  /**
    * Refines the travel time prediction model by adding a data point.
    *
    * @param direction  The direction from which the taxi arrived.
    * @param distance   The Euclidean distance that the taxi traveled.
    * @param travelTime The actual travel time of the taxi.
    */
  def refineModel(direction: Int, distance: Double, travelTime: Double): Unit =
    models(getDirectionBucket(direction)).addData(distance, travelTime)


  /**
    * Converts a direction angle (degrees) into a bucket number.
    *
    * @param direction An angle in degrees.
    * @return A direction bucket number.
    */
  private def getDirectionBucket(direction: Int) = (direction / TravelTimePredictionModel.BUCKET_ANGLE).toByte
}
