package com.lightbend.killrweather.app

import com.lightbend.kafka.KafkaLocalServer
import com.lightbend.killrweather.EventStore.EventStoreSupport
import com.lightbend.killrweather.WeatherClient.WeatherRecord
import com.lightbend.killrweather.app.eventstore.EventStoreSink
import com.lightbend.killrweather.kafka.MessageListener
import com.lightbend.killrweather.settings.WeatherSettings
import com.lightbend.killrweather.utils._
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.{Seconds, State, StateSpec, StreamingContext}
import org.apache.spark.util.StatCounter

import scala.collection.mutable.ListBuffer

object KillrWeatherEventStore {

  def main(args: Array[String]): Unit = {

    // Create context

    val settings = WeatherSettings()
    import settings._

    // Initialize Event Store
    val ctx = EventStoreSupport.createContext()
    EventStoreSupport.ensureTables(ctx)
    println(s"Event Store initialised")

    // Create embedded Kafka and topic
    val kafka = KafkaLocalServer(true)
    kafka.start()
    kafka.createTopic(kafkaConfig.topic)

    println(s"Kafka Cluster created")
    val brokers = "localhost:9092"

    val spark = SparkSession
      .builder()
      .appName("KillrWeather")
      .master("local[*]")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .getOrCreate()

    val ssc = new StreamingContext(spark.sparkContext, Seconds(streamingConfig.batchInterval.toSeconds))
    ssc.checkpoint(streamingConfig.checkpointDir)


    // Create raw data observations stream
    val kafkaParams = MessageListener.consumerProperties(
      brokers,
      kafkaConfig.group, classOf[ByteArrayDeserializer].getName, classOf[ByteArrayDeserializer].getName
    )
    val topics = List(kafkaConfig.topic)

    // Create broadcast variable for the sink definition
    val eventStoreSink = spark.sparkContext.broadcast(EventStoreSink())

    val kafkaDataStream = KafkaUtils.createDirectStream[Array[Byte], Array[Byte]](
      ssc, PreferConsistent, Subscribe[Array[Byte], Array[Byte]](topics, kafkaParams)
    )

    val kafkaStream = kafkaDataStream.map(r => WeatherRecord.parseFrom(r.value()))

    /** Saves the raw data to EventStore - raw table. */
    kafkaStream.foreachRDD { spark.createDataFrame(_).foreachPartition(eventStoreSink.value.writeRaw(_)) }

    // Calculate daily
    val dailyMappingFunc = (station: String, reading: Option[WeatherRecord], state: State[ListBuffer[WeatherRecord]]) => {
      val current = state.getOption().getOrElse(new ListBuffer[WeatherRecord])
      var daily: Option[(String, DailyWeatherData)] = None
      val last = current.lastOption.getOrElse(null.asInstanceOf[WeatherRecord])
      reading match {
        case Some(weather) => {
          current match {
            case sequence if ((sequence.size > 0) && (weather.day != last.day)) => {
              // The day has changed
              val dailyPrecip = sequence.foldLeft(.0)(_ + _.oneHourPrecip)
              val tempAggregate = StatCounter(sequence.map(_.temperature))
              val windAggregate = StatCounter(sequence.map(_.windSpeed))
              val pressureAggregate = StatCounter(sequence.map(_.pressure).filter(_ > 1.0)) // remove 0 elements
              daily = Some((last.wsid, DailyWeatherData(last.wsid, last.year, last.month, last.day,
                tempAggregate.max, tempAggregate.min, tempAggregate.mean, tempAggregate.stdev, tempAggregate.variance,
                windAggregate.max, windAggregate.min, windAggregate.mean, windAggregate.stdev, windAggregate.variance,
                pressureAggregate.max, pressureAggregate.min, pressureAggregate.mean, pressureAggregate.stdev, pressureAggregate.variance,
                dailyPrecip)))
              current.clear()
            }
            case _ =>
          }
          current += weather
          state.update(current)
        }
        case None =>
      }
      daily
    }

    // Define StateSpec<KeyType,ValueType,StateType,MappedType> - types are derived from function
    val dailyStream = kafkaStream.map(r => (r.wsid, r)).mapWithState(StateSpec.function(dailyMappingFunc))
      .filter(_.isDefined).map(_.get)

    // Just for testing
    dailyStream.print()

    // Save daily temperature
    dailyStream.map(ds => DailyTemperature(ds._2))
      .foreachRDD { spark.createDataFrame(_).foreachPartition(eventStoreSink.value.writeDailyTemperature(_)) }

    // Save daily wind
    dailyStream.map(ds => DailyWindSpeed(ds._2))
      .foreachRDD { spark.createDataFrame(_).foreachPartition(eventStoreSink.value.writeDailyWind(_)) }

    // Save daily pressure
    dailyStream.map(ds => DailyPressure(ds._2))
      .foreachRDD { spark.createDataFrame(_).foreachPartition(eventStoreSink.value.writeDailyPressure(_)) }

    // Save daily presipitations
    dailyStream.map(ds => DailyPrecipitation(ds._2))
      .foreachRDD { spark.createDataFrame(_).foreachPartition(eventStoreSink.value.writeDailyPresip(_)) }

    // Calculate monthly
    val monthlyMappingFunc = (station: String, reading: Option[DailyWeatherDataProcess], state: State[ListBuffer[DailyWeatherDataProcess]]) => {
      val current = state.getOption().getOrElse(new ListBuffer[DailyWeatherDataProcess])
      var monthly: Option[(String, MonthlyWeatherData)] = None
      val last = current.lastOption.getOrElse(null.asInstanceOf[DailyWeatherDataProcess])
      reading match {
        case Some(weather) => {
          current match {
            case sequence if ((sequence.size > 0) && (weather.month != last.month)) => {
              // The day has changed
              val tempAggregate = StatCounter(sequence.map(_.temp))
              val windAggregate = StatCounter(sequence.map(_.wind))
              val pressureAggregate = StatCounter(sequence.map(_.pressure))
              val presipAggregate = StatCounter(sequence.map(_.precip))
              monthly = Some((last.wsid, MonthlyWeatherData(last.wsid, last.year, last.month,
                tempAggregate.max, tempAggregate.min, tempAggregate.mean, tempAggregate.stdev, tempAggregate.variance,
                windAggregate.max, windAggregate.min, windAggregate.mean, windAggregate.stdev, windAggregate.variance,
                pressureAggregate.max, pressureAggregate.min, pressureAggregate.mean, pressureAggregate.stdev, pressureAggregate.variance,
                presipAggregate.max, presipAggregate.min, presipAggregate.mean, presipAggregate.stdev, presipAggregate.variance)))
              current.clear()
            }
            case _ =>
          }
          current += weather
          state.update(current)
        }
        case None =>
      }
      monthly
    }

    val monthlyStream = dailyStream.map(r => (r._1, DailyWeatherDataProcess(r._2))).
      mapWithState(StateSpec.function(monthlyMappingFunc)).filter(_.isDefined).map(_.get)

    // Save monthly temperature
    monthlyStream.map(ds => MonthlyTemperature(ds._2))
      .foreachRDD { spark.createDataFrame(_).foreachPartition(eventStoreSink.value.writeMothlyTemperature(_)) }

    // Save monthly wind
    monthlyStream.map(ds => MonthlyWindSpeed(ds._2))
      .foreachRDD { spark.createDataFrame(_).foreachPartition(eventStoreSink.value.writeMothlyWind(_)) }

    // Save monthly pressure
    monthlyStream.map(ds => MonthlyPressure(ds._2))
      .foreachRDD { spark.createDataFrame(_).foreachPartition(eventStoreSink.value.writeMothlyPressure(_)) }

    // Save monthly precipitations
    monthlyStream.map(ds => MonthlyPrecipitation(ds._2))
      .foreachRDD { spark.createDataFrame(_).foreachPartition(eventStoreSink.value.writeMothlyPresip(_)) }

    // Execute
    ssc.start()
    ssc.awaitTermination()
  }
}
