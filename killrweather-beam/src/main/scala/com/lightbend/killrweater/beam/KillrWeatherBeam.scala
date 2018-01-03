package com.lightbend.killrweater.beam

import java.util

import com.datastax.driver.core.Cluster
import com.lightbend.kafka.KafkaLocalServer
import com.lightbend.killrweater.beam.cassandra._
import com.lightbend.killrweater.beam.coders.ScalaStringCoder
import com.lightbend.killrweater.beam.data.{DailyWeatherData, DataObjects, MonthlyWeatherData, RawWeatherData}
import com.lightbend.killrweater.beam.kafka.JobConfiguration
import com.lightbend.killrweater.beam.processors._
import com.lightbend.killrweather.settings.WeatherSettings
import org.apache.beam.sdk.Pipeline
import org.apache.beam.sdk.coders.{ByteArrayCoder, KvCoder, NullableCoder, SerializableCoder}
import org.apache.beam.sdk.io.cassandra.CassandraIO
import org.apache.beam.sdk.io.kafka.KafkaIO
import org.apache.beam.sdk.transforms.ParDo
import com.datastax.driver.core.ConsistencyLevel._
import com.lightbend.killrweater.beam.grafana.GrafanaSetup
import com.lightbend.killrweater.beam.influxdb.DataTransformers

object KillrWeatherBeam {

  def main(args: Array[String]): Unit = {

    WeatherSettings.handleArgs("KillrWeather", args)

    val settings = new WeatherSettings()
    import settings._

    // Initialize Cassandra
    val cluster = Cluster.builder().addContactPoint("127.0.0.1").withPort(CassandraNativePort).build()
    val session = cluster.connect()
    CassandraSetup.setup(session)
    session.close()
    cluster.close()

    // Initialize Grafana
    new GrafanaSetup(14625, "grafana.marathon.mesos").setGrafana()

    // Create and initialize pipeline
    val options = JobConfiguration.initializePipeline(args)

    // Create embedded Kafka and topic
    val kafka = KafkaLocalServer(true)
    kafka.start()
    kafka.createTopic(options.getKafkaDataTopic)

    // Initialize pipeline
    val p = Pipeline.create(options)

    // Coder to use for Kafka data - raw byte message
    val kafkaDataCoder = KvCoder.of(NullableCoder.of(ByteArrayCoder.of), ByteArrayCoder.of)

    // Data Stream - gets data records from Kafka topic
    // It has to use KV to work with state, which is always KV

    val raw = p
      .apply("data", KafkaIO.readBytes
        .withBootstrapServers(options.getBroker)
        .withTopics(util.Arrays.asList(options.getKafkaDataTopic))
        .updateConsumerProperties(JobConfiguration.getKafkaConsumerProps(options))
        .withoutMetadata).setCoder(kafkaDataCoder)
      .apply("Convert to raw record", ParDo.of(new ConvertDataRecordFn))
//      .apply("Print it", ParDo.of(new SimplePrintFn[RawWeatherData]("Raw data")))
/*
    raw
      .apply("Convert to Cassandra entity", ParDo.of(new CassandraTransformFn[RawWeatherData, RawEntity](Entities.getRawEntity)))
          .setCoder(SerializableCoder.of(classOf[RawEntity]))
      .apply(CassandraIO.write[RawEntity]()
        .withHosts(util.Arrays.asList("127.0.0.1"))
        .withPort(CassandraNativePort)
        .withConsistencyLevel(LOCAL_ONE.toString)
        .withKeyspace(CassandraKeyspace)
        .withEntity(classOf[RawEntity]))
*/
    raw.apply("Write to Cassandra", ParDo.of(new WriteRawToCassandraFn("127.0.0.1", CassandraNativePort)))
    raw.apply("Write to Influx", ParDo.of(new WriteToInfluxDBFn[RawWeatherData](DataTransformers.getRawPoint)))

    val daily = raw
      .apply("Calculate dayily", ParDo.of(new GroupIntoBatchesFn[String, RawWeatherData, DailyWeatherData]
      (ScalaStringCoder.of, SerializableCoder.of(classOf[RawWeatherData]),
        DataObjects.getRawTrigger, DataObjects.convertRawData)))
      .setCoder(KvCoder.of(ScalaStringCoder.of, SerializableCoder.of(classOf[DailyWeatherData])))
//      .apply("Print it", ParDo.of(new SimplePrintFn[DailyWeatherData]("Daily data")))
/*
    // Store daily data to Cassandra
    daily
      .apply("Convert to Cassandra temperature", ParDo.of(new CassandraTransformFn[DailyWeatherData, DailyTemperatureEntity](Entities.getDailyTempEntity)))
      .setCoder(SerializableCoder.of(classOf[DailyTemperatureEntity]))
      .apply(CassandraIO.write[DailyTemperatureEntity]()
        .withHosts(util.Arrays.asList("127.0.0.1"))
        .withPort(CassandraNativePort)
        .withConsistencyLevel(LOCAL_ONE.toString)
        .withKeyspace(CassandraKeyspace)
        .withEntity(classOf[DailyTemperatureEntity]))

    daily
      .apply("Convert to Cassandra pressure", ParDo.of(new CassandraTransformFn[DailyWeatherData, DailyPressureEntity](Entities.getDailyPressureEntity)))
      .setCoder(SerializableCoder.of(classOf[DailyPressureEntity]))
      .apply(CassandraIO.write[DailyPressureEntity]()
        .withHosts(util.Arrays.asList("127.0.0.1"))
        .withPort(CassandraNativePort)
        .withConsistencyLevel(LOCAL_ONE.toString)
        .withKeyspace(CassandraKeyspace)
        .withEntity(classOf[DailyPressureEntity]))

    daily
      .apply("Convert to Cassandra wind", ParDo.of(new CassandraTransformFn[DailyWeatherData, DailyWindEntity](Entities.getDailyWindEntity)))
      .setCoder(SerializableCoder.of(classOf[DailyWindEntity]))
      .apply(CassandraIO.write[DailyWindEntity]()
        .withHosts(util.Arrays.asList("127.0.0.1"))
        .withPort(CassandraNativePort)
        .withConsistencyLevel(LOCAL_ONE.toString)
        .withKeyspace(CassandraKeyspace)
        .withEntity(classOf[DailyWindEntity]))

    daily
      .apply("Convert to Cassandra precip", ParDo.of(new CassandraTransformFn[DailyWeatherData, DailyPrecipEntity](Entities.getDailyPrecipEntity)))
      .setCoder(SerializableCoder.of(classOf[DailyPrecipEntity]))
      .apply(CassandraIO.write[DailyPrecipEntity]()
        .withHosts(util.Arrays.asList("127.0.0.1"))
        .withPort(CassandraNativePort)
        .withConsistencyLevel(LOCAL_ONE.toString)
        .withKeyspace(CassandraKeyspace)
        .withEntity(classOf[DailyPrecipEntity]))
*/
    daily.apply("Write to Cassandra", ParDo.of(new WriteDailyToCassandraFn("127.0.0.1", CassandraNativePort)))
    daily.apply("Write to Influx", ParDo.of(new WriteToInfluxDBFn[DailyWeatherData](DataTransformers.getDaylyPoint)))

    val monthly = daily
      .apply("Calculate monthly", ParDo.of(new GroupIntoBatchesFn[String, DailyWeatherData, MonthlyWeatherData]
      (ScalaStringCoder.of, SerializableCoder.of(classOf[DailyWeatherData]),
        DataObjects.getDailyTrigger, DataObjects.convertDailyData)))
      .setCoder(KvCoder.of(ScalaStringCoder.of, SerializableCoder.of(classOf[MonthlyWeatherData])))
      .apply("Print it", ParDo.of(new SimplePrintFn[MonthlyWeatherData]("Monthly data")))
/*
    // Store monthly data to Cassandra
    monthly
      .apply("Convert to Cassandra temperature", ParDo.of(new CassandraTransformFn[MonthlyWeatherData, MonthlyTemperatureEntity](Entities.getMonthlyTempEntity)))
      .setCoder(SerializableCoder.of(classOf[MonthlyTemperatureEntity]))
      .apply(CassandraIO.write[MonthlyTemperatureEntity]()
        .withHosts(util.Arrays.asList("127.0.0.1"))
        .withPort(CassandraNativePort)
        .withConsistencyLevel(LOCAL_ONE.toString)
        .withKeyspace(CassandraKeyspace)
        .withEntity(classOf[MonthlyTemperatureEntity]))

    monthly
      .apply("Convert to Cassandra pressure", ParDo.of(new CassandraTransformFn[MonthlyWeatherData, MonthlyPressureEntity](Entities.getMonthlyPressureEntity)))
      .setCoder(SerializableCoder.of(classOf[MonthlyPressureEntity]))
      .apply(CassandraIO.write[MonthlyPressureEntity]()
        .withHosts(util.Arrays.asList("127.0.0.1"))
        .withPort(CassandraNativePort)
        .withConsistencyLevel(LOCAL_ONE.toString)
        .withKeyspace(CassandraKeyspace)
        .withEntity(classOf[MonthlyPressureEntity]))

    monthly
      .apply("Convert to Cassandra wind", ParDo.of(new CassandraTransformFn[MonthlyWeatherData, MonthlyWindEntity](Entities.getMonthlyWindEntity)))
      .setCoder(SerializableCoder.of(classOf[MonthlyWindEntity]))
      .apply(CassandraIO.write[MonthlyWindEntity]()
        .withHosts(util.Arrays.asList("127.0.0.1"))
        .withPort(CassandraNativePort)
        .withConsistencyLevel(LOCAL_ONE.toString)
        .withKeyspace(CassandraKeyspace)
        .withEntity(classOf[MonthlyWindEntity]))

    monthly
      .apply("Convert to Cassandra precip", ParDo.of(new CassandraTransformFn[MonthlyWeatherData, MonthlyPrecipEntity](Entities.getMonthlyPrecipEntity)))
      .setCoder(SerializableCoder.of(classOf[MonthlyPrecipEntity]))
      .apply(CassandraIO.write[MonthlyPrecipEntity]()
        .withHosts(util.Arrays.asList("127.0.0.1"))
        .withPort(CassandraNativePort)
        .withConsistencyLevel(LOCAL_ONE.toString)
        .withKeyspace(CassandraKeyspace)
        .withEntity(classOf[MonthlyPrecipEntity]))
*/
    monthly.apply("Write to Cassandra", ParDo.of(new WriteMonthlyToCassandraFn("127.0.0.1", CassandraNativePort)))
    monthly.apply("Write to Influx", ParDo.of(new WriteToInfluxDBFn[MonthlyWeatherData](DataTransformers.getMonthlyPoint)))

    // Run the pipeline
    p.run
  }
}
