# KillrWeather

KillrWeather is a reference application (which is adopted from Datastax's https://github.com/killrweather/killrweather) showing how to easily leverage and integrate [Apache Spark](http://spark.apache.org), [Apache Cassandra](http://cassandra.apache.org), [Apache Kafka](http://kafka.apache.org), [Akka](https://akka.io), and [InfluxDB](https://www.influxdata.com/) for fast, streaming computations. This application focuses on the use case of **[time series data](https://github.com/killrweather/killrweather/wiki/4.-Time-Series-Data-Model)**.

This application also can be viewed as a prototypical IoT (or sensors) data collection application, which stores data in the form of a time series.

> **Disclaimer:** This sample application is provided as-is, without warranty. It is intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but it has not gone through a robust validation process, nor does it use all the techniques commonly employed for highly-resilient, production applications. Please use it with appropriate caution.

## Sample Use Case

_I need fast access to historical data on the fly for predictive modeling with real time data from the stream._

The application does not quite do that, it stops at capturing real-time and cumulative information.

## Reference Application

There are several versions this application:
* [KillrWeather App](https://github.com/killrweather/killrweather/tree/master/killrweather-app/src/main/scala/com/datastax/killrweather) is based on Spark Streaming.
Additional class here [KillrWeather Event Store](https://github.com/lightbend/fdp-killrweather/blob/master/killrweather-app/src/main/scala/com/lightbend/killrweather/app/KillrWeatherEventStore.scala)
is an experimental implementation replacing Cassandra with IBM's [EventStore](https://www.ibm.com/us-en/marketplace/db2-event-store). Runs only locally (using embedded Kafka) and requires [Event Store Development Edition](https://www-01.ibm.com/marketing/iwm/iwm/web/preLogin.do?source=swerpal-eventstore-3)
* [KillrWeather App Structured](https://github.com/lightbend/fdp-killrweather/blob/master/killrweather-app_structured/src/main/scala/com/lightbend/killrweather/app/structured/KillrWeatherStructured.scala) is a version of the same, based on Spark Structured Streaming.
* [KillrWeather Beam](https://github.com/lightbend/fdp-killrweather/blob/master/killrweather-beam/src/main/scala/com/lightbend/killrweater/beam/KillrWeatherBeam.scala) experimental version of the same application based on [Apache Beam](https://beam.apache.org/).
This version only runs locally (using embedded Kafka). Cluster version is coming soon

## Time Series Data

The use of time series data for business analysis is not new. What is new is the ability to collect and analyze massive volumes of data in sequence at extremely high velocity to get the clearest picture to predict and forecast future market changes, user behavior, environmental conditions, resource consumption, health trends and much, much more.

Apache Cassandra is a NoSQL database platform particularly suited for these types of Big Data challenges. Cassandra’s data model is an excellent fit for handling data in sequence regardless of data type or size. When writing data to Cassandra, data is sorted and written sequentially to disk. When retrieving data by row key and then by range, you get a fast and efficient access pattern due to minimal disk seeks – time series data is an excellent fit for this type of pattern. Apache Cassandra allows businesses to identify meaningful characteristics in their time series data as fast as possible to make clear decisions about expected future outcomes.

There are many flavors of time series data. Some are windows into the stream, others cannot be windows, because the queries are not by time slice but by specific year, month, day, hour, etc. Spark Streaming lets you do both.

In addition to Cassandra, the application also demonstrates integration with InfluxDB and Grafana. This toolset is typically used for DevOps monitoring purposes, but it can also be very useful for real-time visualization of stream processing. Data is written to influxDB as it gets ingested in the application and Grafana is used to provide a real time view of temperature, pressure, and dewpoint values, as the reports come in. Additionally, the application provides results of data rollups - daily and monthly mean, low, and high values.

## Start Here

The original [KillrWeather Wiki](https://github.com/killrweather/killrweather/wiki) is still a great source of information.

## Using Applications

We foresee 2 groups of users:
* Users that just want to see how application runs. For this type of users, [fdp-package-sample-apps](https://github.com/typesafehub/fdp-package-sample-apps)
provides pre build version of software. Refer to [Readme](https://github.com/typesafehub/fdp-package-sample-apps/blob/develop/README.md)
for this project on how to deploy and run the project
* Users that want to use this project as a starting point for their own implementation.
This users need to know how to build the project and run it locally and on the cluster.
This information is provided in [README-DEVELOPERS](https://github.com/lightbend/fdp-killrweather/blob/master/README-DEVELOPERS.md)

## See What's Going On...

Go to the Spark Web console for this job at http://killrweatherapp.marathon.mesos:4040/jobs/
or http://killrweatherappstructured.marathon.mesos:4040/jobs/ (if structured streaming version is used)
to see the minibatch and other jobs that are executed as part of this Spark Streaming job.

Go to http://leader.mesos/mesos/#/frameworks and search for killrweatherapp or killrweatherappstructured to get more info about the corresponding executors.

## Loading data

The application can use three different loaders, which are local client applications that can communicate with the corresponding cluster services or clients to send weather reports.:

1. Direct Kafka loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngester` pushes data directly to the Kafka queue
that an application is listening on.
2. HTTP loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngesterRest` writes data to KillrWeather HTTP client.
3. GRPC loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngesterGRPC` writes data to KillrWeather GRPC client.

Use the commands we saw previously for data loading to run these commands locally.

## Monitoring and Viewing Results

For information about setting up Grafana and InfluxDB, see this [article](https://mesosphere.com/blog/monitoring-dcos-cadvisor-influxdb-grafana/).

Monitoring is done using InfluxDB and Grafana.

To open the Grafana UI, click the `grafana` service in the DC/OS _Services_ panel, then click the instance link.
Now click the URL for the `ENDPOINTS`.

> **Note:** If you are in a DC/OS EE cluster, the link will open with `https`. If this fails to load, replace with `http`.

In the Grafana UI, load the definitions in `./killrweather-app/src/main/resource/grafana.json`. (Click the upper-left-hand side Grafana icon, then _Dashboards_, then _Import_.) This will create a dashboard called _KillrWeather Data Ingestion_.

Once set up and once data is flowing through the system, you can view activity in this dashboard.

Applications themselves currently implement setup. So this information is here just for reference.


To view execution results, a Zeppelin notebook is used, configured for [Cassandra in Zeppelin](https://zeppelin.apache.org/docs/0.7.2/interpreter/cassandra.html).

To configure Zeppelin for use of Cassandra, make sure that interpreter is configured correctly. The most important settings are:

```
name	                   value
cassandra.cluster        cassandra
cassandra.hosts	         node.cassandra.l4lb.thisdcos.directory
cassandra.native.port	   9042
```

Create a notebook. Try the following, one per notebook cell:

```
%cassandra
use isd_weather_data;

%cassandra
select day, wsid, high, low, mean,stdev,variance from daily_aggregate_temperature WHERE year=2008 and month=6 allow filtering;

%cassandra
select wsid, day, high, low, mean,stdev,variance from daily_aggregate_pressure  WHERE year=2008 and month=6 allow filtering;

%cassandra
select wsid, month, high, low, mean,stdev,variance from monthly_aggregate_temperature WHERE year=2008  allow filtering;

%cassandra
select wsid, month, high, low, mean,stdev,variance from monthly_aggregate_pressure WHERE year=2008  allow filtering;

```

