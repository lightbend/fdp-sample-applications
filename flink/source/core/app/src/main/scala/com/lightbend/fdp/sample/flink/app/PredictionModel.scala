package com.lightbend.fdp.sample.flink.app


import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.time.Time
import org.apache.flink.api.common.typeinfo.{TypeHint, TypeInformation}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector


/**
  * Predicts the travel time for taxi ride start events based on distance and direction.
  * Incrementally trains a regression model using taxi ride end events.
  */
class PredictionModel extends RichFlatMapFunction[(Int, TaxiRide), PredictedTime] {
  // model state
  var modelState: ValueState[TravelTimePredictionModel] = _

  override def open(parameters: Configuration): Unit = {
    // obtain key-value state for prediction model
    val descriptor = new ValueStateDescriptor[TravelTimePredictionModel](
      // state name
      "regressionModel",
      // type info for state object
      TypeInformation.of(classOf[TravelTimePredictionModel])
    )

    modelState = getRuntimeContext.getState(descriptor)
  }

  override def flatMap(in: (Int, TaxiRide), out: Collector[PredictedTime]): Unit = {

    // fetch operator state
    val model: TravelTimePredictionModel = Option(modelState.value).getOrElse(new TravelTimePredictionModel)
    val ride: TaxiRide = in._2

    // compute distance and direction
    val distance =
      GeoUtils.getEuclideanDistance(ride.startLon, ride.startLat, ride.endLon, ride.endLat)
    val direction =
      GeoUtils.getDirectionAngle(ride.endLon, ride.endLat, ride.startLon, ride.startLat)

    if (ride.isStart) {
      // we have a start event: Predict travel time
      val predictedTime: Int = model.predictTravelTime(direction, distance)
      // emit prediction
      out.collect( new PredictedTime(ride.rideId, predictedTime) )
    }
    else {
      // we have an end event: Update model
      // compute travel time in minutes
      val travelTime = (ride.endTime.getMillis - ride.startTime.getMillis) / 60000.0
      // refine model
      model.refineModel(direction, distance, travelTime)
      // update operator state
      modelState.update(model)
    }
  }
}
