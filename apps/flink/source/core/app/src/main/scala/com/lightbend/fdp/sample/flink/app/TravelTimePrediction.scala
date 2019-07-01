package com.lightbend.fdp.sample.flink.app

import java.util.concurrent.TimeUnit
import java.util.Properties

import com.lightbend.fdp.sample.flink.app.utils.GeoUtils
import com.lightbend.fdp.sample.flink.models.{PredictedTime, TaxiRide}
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.time.Time
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.runtime.state.filesystem.FsStateBackend
import org.apache.flink.streaming.api.environment.CheckpointConfig.ExternalizedCheckpointCleanup
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.{CheckpointingMode, TimeCharacteristic}
import org.apache.flink.streaming.api.windowing.time.{Time => StreamingTime}
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, FlinkKafkaProducer}

object TravelTimePrediction {

  private val RIDE_SPEED_GROUP = s"rideSpeedGroup${System.currentTimeMillis}"
  val MAX_EVENT_DELAY = 60 // events are out of order by max 60 seconds
  
  def main(args: Array[String]): Unit = {

    // parse parameters
    val params = ParameterTool.fromArgs(args)

    // bootstrap servers
    val brokers = params.getRequired("broker-list")

    // topic from where data will come
    val inTopic = params.getRequired("inTopic")

    // topic to write output to
    val outTopic = params.getRequired("outTopic")

    println(s"Starting Flink Travel time prediction, brokers $brokers, reading from $inTopic, writing to $outTopic")
    
    // set up the execution environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    // operate in Event-time
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // Enable checkpointing
    env.enableCheckpointing(60000 )   // 1 min
    env.getCheckpointConfig.setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE)
    env.getCheckpointConfig.setMaxConcurrentCheckpoints(1)
    env.getCheckpointConfig.enableExternalizedCheckpoints(ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION)
    val checkpointingBackend = new FsStateBackend("file:///flink/checkpoints", true)
    env.setStateBackend(checkpointingBackend)


    // try to restart 60 times with 10 seconds delay (10 Minutes)
    env.setRestartStrategy(RestartStrategies.fixedDelayRestart(5, Time.of(10, TimeUnit.SECONDS)))

    // configure Kafka consumer
    val kafkaProps = new Properties
    kafkaProps.setProperty("bootstrap.servers", brokers)
    kafkaProps.setProperty("group.id", RIDE_SPEED_GROUP)

    // always read the Kafka topic from the start
    kafkaProps.setProperty("auto.offset.reset", "latest")

    // create a Kafka consumer
    val consumer = new FlinkKafkaConsumer[TaxiRide](
      inTopic,
      new TaxiRideSchema,
      kafkaProps)

    // configure timestamp and watermark assigner
    consumer.assignTimestampsAndWatermarks(new TaxiRideTSAssigner)
    // create a Kafka source
    // get the taxi ride data stream
    val rides = env.addSource(consumer)

    val filteredRides = rides

      // filter out rides that do not start and end in NYC
      .filter(r => GeoUtils.isInNYC(r.startLon, r.startLat) && GeoUtils.isInNYC(r.endLon, r.endLat))

      // map taxi ride events to the grid cell of the destination
      .map(r => (GeoUtils.mapToGridCell(r.endLon, r.endLat), r))

      // organize stream by destination
      .keyBy(_._1)

      // predict and refine model per destination
      .flatMap(new PredictionModel())

    // output the predictions
    filteredRides.addSink(
      new FlinkKafkaProducer[PredictedTime](
        brokers,
        outTopic,
        new PredictedTimeSchema))

    // run the prediction pipeline
    val _ = env.execute("Travel Time Prediction")
  }
}

/**
  * Assigns timestamps to TaxiRide records.
  * Watermarks are periodically assigned, a fixed time interval behind the max timestamp.
  */
class TaxiRideTSAssigner
  extends BoundedOutOfOrdernessTimestampExtractor[TaxiRide](StreamingTime.seconds(TravelTimePrediction.MAX_EVENT_DELAY)) {

  override def extractTimestamp(ride: TaxiRide): Long = {
    if(ride.isStart) ride.startTime.getMillis else ride.endTime.getMillis
  }
}

