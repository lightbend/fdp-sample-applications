package com.lightbend.fdp.sample.flink.app

import com.lightbend.fdp.sample.flink.app.model.TravelTimePredictionModel
import com.lightbend.fdp.sample.flink.app.utils.GeoUtils
import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
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
    val model = Option(modelState.value).getOrElse(new TravelTimePredictionModel)
    val ride = in._2

    // compute distance, direction and travel time in minutes
    val distance = GeoUtils.getEuclideanDistance(ride.startLon, ride.startLat, ride.endLon, ride.endLat)
    val direction = GeoUtils.getDirectionAngle(ride.endLon, ride.endLat, ride.startLon, ride.startLat)

    // Process ride
    ride.isStart match {
      case true => // we have a start event: Predict travel time
        model.predictTravelTime(direction, distance) match {
          case predictedTime if(predictedTime < 0) => // No prediction
//            println(s"Could not predict time for ride $ride")
          case predictedTime => // Get the result
            // emit prediction
            println(s"Predicted time for ride ${ride.rideId}, predicted time $predictedTime")
            out.collect( new PredictedTime(ride.rideId, predictedTime) )
        }
      case _ => // we have an end event: Update model
        // refine model
        val travelTime = (ride.endTime.getMillis - ride.startTime.getMillis) / 60000.0
        model.refineModel(direction, distance, travelTime)
        // update operator state
        modelState.update(model)
    }
  }
}
