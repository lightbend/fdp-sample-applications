package com.lightbend.killrweather.app

import com.lightbend.killrweather.kafka.{EmbeddedSingleNodeKafkaCluster, MessageListener}
import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Seconds, State, StateSpec, StreamingContext}
import com.lightbend.killrweather.settings.WeatherSettings
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import com.lightbend.killrweather.utils.Weather
import com.datastax.spark.connector.streaming._
import org.apache.spark.util.StatCounter

import scala.collection.mutable.ListBuffer

/**
  * Created by boris on 7/9/17.
  */
object KillrWeather {

  def main(args: Array[String]): Unit = {

    val settings = new WeatherSettings()

    import settings._
    import Weather._
    // Create embedded Kafka and topic
    EmbeddedSingleNodeKafkaCluster.start()
    EmbeddedSingleNodeKafkaCluster.createTopic(KafkaTopicRaw)
//    val brokers = "localhost:9092"
    val brokers = EmbeddedSingleNodeKafkaCluster.bootstrapServers

    // Create context

    val sparkConf = new SparkConf().setAppName(AppName).setMaster(SparkMaster)
      .set("spark.cassandra.connection.host", CassandraHosts)
    sparkConf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .set("spark.cassandra.connection.host", CassandraHosts)
    val ssc = new StreamingContext(sparkConf, Seconds(SparkStreamingBatchInterval/1000))
    ssc.checkpoint(SparkCheckpointDir)
    val sc = ssc.sparkContext

    // Create raw data observations stream
    val kafkaParams = MessageListener.consumerProperties(brokers,
            KafkaGroupId, classOf[StringDeserializer].getName, classOf[StringDeserializer].getName)
    val topics = List(KafkaTopicRaw)

    // Initial state RDD for current models
    val dailyRDD = ssc.sparkContext.emptyRDD[(String, ListBuffer[RawWeatherData])]

    val kafkaDataStream = KafkaUtils.createDirectStream[String, String](
      ssc, PreferConsistent, Subscribe[String, String] (topics,kafkaParams))

    val kafkaStream = kafkaDataStream.map(_.value().split(",")).map(RawWeatherData(_))

    /** Saves the raw data to Cassandra - raw table. */
    kafkaStream.saveToCassandra(CassandraKeyspace, CassandraTableRaw)

    // Calculate daily
    val dailyMappingFunc = (station: String, reading: Option[RawWeatherData], state: State[ListBuffer[RawWeatherData]]) => {
      val current = state.getOption().getOrElse(new ListBuffer[RawWeatherData])
      var daily : Option[(String,DailyWeatherData)] = None
      val last = current.lastOption.getOrElse(null.asInstanceOf[RawWeatherData])
      reading match {
        case Some(weather) => {
          current match {
            case sequence if((sequence.size > 0) && (weather.day != last.day)) => {
              // The day has changed
              val dailyPrecip = sequence.foldLeft(.0) (_ + _.oneHourPrecip)
              val tempAggregate = StatCounter(sequence.map(_.temperature))
              daily = Some(last.wsid, DailyWeatherData(last.wsid, last.year, last.month, last.day, tempAggregate.max,
                tempAggregate.min, tempAggregate.mean, tempAggregate.stdev, tempAggregate.variance, dailyPrecip))
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
    val dailyStream = kafkaStream.map(r => (r.wsid, r)).mapWithState(StateSpec.function(dailyMappingFunc).initialState(dailyRDD))
        .filter(_.isDefined).map(_.get)

    // Just for testing
    dailyStream.print()

    // Save daily temperature
    dailyStream.map(ds => (ds._2.wsid, ds._2.year, ds._2.month, ds._2.day, ds._2.high, ds._2.low, ds._2.mean, ds._2.stdev,
      ds._2.variance)).saveToCassandra(CassandraKeyspace, CassandraTableDailyTemp)


    // Save daily presips
    dailyStream.map(ds => (ds._2.wsid, ds._2.year, ds._2.month, ds._2.day, ds._2.precip)).
      saveToCassandra(CassandraKeyspace, CassandraTableDailyPrecip)

    // Execute
    ssc.start()
    ssc.awaitTermination()
  }
}
