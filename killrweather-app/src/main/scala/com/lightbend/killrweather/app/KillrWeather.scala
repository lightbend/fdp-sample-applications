package com.lightbend.killrweather.app

import org.apache.spark.streaming._
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import com.datastax.spark.connector.streaming._
import com.lightbend.killrweather.kafka.MessageListener
import com.lightbend.killrweather.settings.WeatherSettings
import com.lightbend.killrweather.WeatherClient.WeatherRecord
import com.lightbend.killrweather.app.cassandra.CassandraSetup
import com.lightbend.killrweather.app.influxdb.InfluxDBSink
import com.lightbend.killrweather.utils._
import org.apache.spark.sql.SparkSession
import org.apache.spark.util.StatCounter

import scala.collection.mutable.ListBuffer

object KillrWeather {

  def main(args: Array[String]): Unit = {

    // Create context

    val killrSettings = WeatherSettings("KillrWeather", args)
    import killrSettings._
    println(s"Running Killrweather. Kafka: $kafkaConfig; Cassandra : $cassandraConfig; " +
      s"InfluxDB: $influxConfig; Grafana: $graphanaConfig")

    val sparkSession = SparkSession.builder()
      .config(killrSettings.sparkConf())
      .appName("KillrWeather")
      .getOrCreate()

    // Initialize Cassandra
    try {
      val client = new CassandraSetup(sparkConf).setup()
    } catch {
      case t: Throwable => println("Cassandra not initialized")
    }
    val sc = sparkSession.sparkContext
    val ssc = new StreamingContext(sc, Duration(streamingConfig.batchInterval.toMillis))

    ssc.checkpoint(streamingConfig.checkpointDir)

    // Create raw data observations stream
    val kafkaParams = MessageListener.consumerProperties(
      kafkaConfig.brokers,
      kafkaConfig.group, classOf[ByteArrayDeserializer].getName, classOf[ByteArrayDeserializer].getName
    )
    val topics = List(kafkaConfig.topic)

    // Initial state RDD for daily accumulator
    val dailyRDD = ssc.sparkContext.emptyRDD[(String, ListBuffer[WeatherRecord])]

    // Initial state RDD for monthly accumulator
    val monthlyRDD = ssc.sparkContext.emptyRDD[(String, ListBuffer[DailyWeatherDataProcess])]

    // Create broadcast variable for the sink definition
    val influxDBSink = sc.broadcast(InfluxDBSink())

    val kafkaDataStream = KafkaUtils.createDirectStream[Array[Byte], Array[Byte]](
      ssc, PreferConsistent, Subscribe[Array[Byte], Array[Byte]](topics, kafkaParams)
    )

    val kafkaStream = kafkaDataStream.map(r => {
      val wr = WeatherRecord.parseFrom(r.value())
      influxDBSink.value.write(wr)
      wr
    })

    /** Saves the raw data to Cassandra - raw table. */
    kafkaStream.saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableRaw)

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
    val dailyStream = kafkaStream.map(r => (r.wsid, r)).mapWithState(StateSpec.function(dailyMappingFunc).initialState(dailyRDD))
      .filter(_.isDefined).map(_.get)

    // Just for testing
    dailyStream.print()

    // Save daily temperature
    dailyStream.map(ds => {
      val dt = DailyTemperature(ds._2)
      influxDBSink.value.write(dt)
      dt
    }).saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableDailyTemp)

    // Save daily wind
    dailyStream.map(ds => DailyWindSpeed(ds._2)).saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableDailyWind)

    // Save daily pressure
    dailyStream.map(ds => DailyPressure(ds._2)).saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableDailyPressure)

    // Save daily precipitations
    dailyStream.map(ds => DailyPrecipitation(ds._2)).saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableDailyPrecip)

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
      mapWithState(StateSpec.function(monthlyMappingFunc).initialState(monthlyRDD)).filter(_.isDefined).map(_.get)

    // Save monthly temperature
    monthlyStream.map(ds => {
      val mt = MonthlyTemperature(ds._2)
      influxDBSink.value.write(mt)
      mt
    }).saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableMonthlyTemp)

    // Save monthly wind
    monthlyStream.map(ds => MonthlyWindSpeed(ds._2)).saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableMonthlyWind)

    // Save monthly pressure
    monthlyStream.map(ds => MonthlyPressure(ds._2)).saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableMonthlyPressure)

    // Save monthly presipitations
    monthlyStream.map(ds => MonthlyPrecipitation(ds._2)).saveToCassandra(cassandraConfig.keyspace, cassandraConfig.tableMonthlyPrecip)

    // Execute
    ssc.start()
    ssc.awaitTermination()
  }
}
