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
  var predictionModel: ValueState[TravelTimePredictionModel] = _

  override def open(parameters: Configuration): Unit = {
    // obtain key-value state for prediction model
    val descriptor = new ValueStateDescriptor[TravelTimePredictionModel](
      // state name
      "regressionModel",
      // type info for state object
      TypeInformation.of(new TypeHint[TravelTimePredictionModel]() {}),
      // state default value
      new TravelTimePredictionModel)

    predictionModel = getRuntimeContext.getState(descriptor)
  }

  override def flatMap(in: (Int, TaxiRide), out: Collector[PredictedTime]): Unit = {
    val model = predictionModel.value()
    val ride = in._2

    val direction = GeoUtils.getDirectionAngle(ride.endLon, ride.endLat, ride.startLon, ride.startLat)
    val distance = GeoUtils.getEuclideanDistance(ride.startLon, ride.startLat, ride.endLon, ride.endLat)

    if (ride.isStart) {
      out.collect(PredictedTime(ride.rideId, model.predictTravelTime(direction, distance)))
    } else {
      model.refineModel(direction, distance, (ride.endTime.getMillis() - ride.startTime.getMillis) / 60000.0)
      predictionModel.update(model)
    }
  }
}
