# KillrWeather

KillrWeather is a reference application (which is adopted from Datastax's https://github.com/killrweather/killrweather) showing how to easily leverage and integrate [Apache Spark](http://spark.apache.org),
[Apache Cassandra](http://cassandra.apache.org), [Apache Kafka](http://kafka.apache.org) and [InfluxDB](https://www.influxdata.com/) for fast, streaming computations. This application focuses on the use case of  **[time series data](https://github.com/killrweather/killrweather/wiki/4.-Time-Series-Data-Model)**.
This application also can be viewed as a prototypical IoT (or sensors) data collection and storing data in the form of time series 
  
## Sample Use Case
I need fast access to historical data  on the fly for  predictive modeling  with real time data from the stream. 
The application does not quite do that, it stops at capturing real-time and cummulative information.

## Reference Application 
[KillrWeather Main App](https://github.com/killrweather/killrweather/tree/master/killrweather-app/src/main/scala/com/datastax/killrweather)

## Time Series Data 
The use of time series data for business analysis is not new. What is new is the ability to collect and analyze massive volumes of data in sequence at extremely high velocity to get the clearest picture to predict and forecast future market changes, user behavior, environmental conditions, resource consumption, health trends and much, much more.

Apache Cassandra is a NoSQL database platform particularly suited for these types of Big Data challenges. Cassandra’s data model is an excellent fit for handling data in sequence regardless of data type or size. When writing data to Cassandra, data is sorted and written sequentially to disk. When retrieving data by row key and then by range, you get a fast and efficient access pattern due to minimal disk seeks – time series data is an excellent fit for this type of pattern. Apache Cassandra allows businesses to identify meaningful characteristics in their time series data as fast as possible to make clear decisions about expected future outcomes.

There are many flavors of time series data. Some can be windowed in the stream, others can not be windowed in the stream because queries are not by time slice but by specific year,month,day,hour. Spark Streaming lets you do both.

In addition to Cassandra application also demonstrates integration with InfluxDB/Grafana. This toolset is used mostly for DevOps purposes, but can be also
very useful for real-time visualization of stream processing. Data is written to influxDB as it gets ingested in the application and Grafana provides real time view
of temperature, pressure and dewpoint values as the reports come in. Additionally application provides results of data rollups - daily and monthly mean, low and high values.

## Start Here
Original [KillrWeather Wiki](https://github.com/killrweather/killrweather/wiki) still is a great source of information


### Build the code 

We recommend using of Intellij for managing and building code. The project is organized as several modules:
* Data - some data files for running applications including commands for initializing Cassandra and actual measurement data
* Diagrams - original diagrams for overall architecture - obsolete
* Killrweather-app - actual Spark application for data processing - ready to run in Spark
* Killrweather-app-local - actual Spark application for data processing - ready to run locally. If running in Intellij make sure that `use classpath of module` is set up to this module
* KillrweatherCore - some support code used throughout an application. Also includes Embedded Kafka allowing to run everything locally
* Killrweather-grcpclient - client for exposing Killrweather app over [GRPC](https://grpc.io/). This client accepts GRPC messages and publishes them to Kafka for application consumption. 
* Killrweather-httpclient - client for exposing Killrweather app over HTTP. This client accepts HTTP messages (in JSON) and publishes them to Kafka for application consumption.
* KillrWeather-loader - a collection of loaders reading reports data (from data directory) and publishing it (via Kafka, GRPC and HTTP) to the application.

Build is done via SBT



    cd killrweather
    sbt compile
    # For IntelliJ users, just import a project and use IntelliJ commands
 

### Setup Locally on Linux or Mac

1.[Download the latest Cassandra](http://cassandra.apache.org/download/) and open the compressed file. For Mac users - use brew

2.Start Cassandra 

3.Run the setup cql scripts to create the schema and populate the weather stations table.
On the command line start a cqlsh shell:

    cd /path/to/killrweather/data
    path/to/apache-cassandra-{version}/bin/cqlsh

4.Comment out InluxDB support - go to `com.lightbend.killrweather.app.influxdb.InfluxDBSink` class (in `Killrweather-app`) and in the `write` method
comment out `influxDB.write(point)` line. Also comment out content of the `f` function implementation to return `null.asInstanceOf[InfluxDB]`


### Cassandra setup:

Start `CQLSH`. You should see something similar to:

     Connected to Test Cluster at 127.0.0.1:9042.
     [cqlsh {latest.version} | Cassandra {latest.version} | CQL spec {latest.version} | Native protocol {latest.version}]
     Use HELP for help.
     cqlsh>

Run the scripts, then keep the cql shell open querying once the apps are running:

     cqlsh> source 'create-timeseries.cql';
     cqlsh> source 'load-timeseries.cql';

Verify the keyspace was added:

     describe keyspaces;

Switch active keyspace context:

     use isd_weather_data ;
List out all tables installed:

     describe tables;
     
Weather stations table should be populated
     
     select * from weather_station limit 5;
     
To clean up data in Cassandra, type:

     
     cqlsh> DROP KEYSPACE isd_weather_data;
     


To close the cql shell:


     
     cqlsh> quit;
     

 

## DC/OS deployment

1. Install Kafka, InfluxDB, Grafana and Cassandra (from DC/OS universe)

2. Application and clients are set up using FDP-Lab as described [here](https://docs.google.com/document/d/1eMG8I4z6mQ0C4Llg1VHnpV7isnVAtnk-pOkDo8tIubI/edit#heading=h.izl4k6rmh4c0)


####Application itself

1. Run `sbt 'deploySsh killrWeatherApp'`. This will package an uberJar spark.jar and push it to FDP lab
2. Use `KillrWeatherApp/src/main/resource/KillrweatherApp.json` to run it as a marathon job
4. Use http://killrweatherapp.marathon.mesos:4040/jobs/ to see execution
5. Go to http://leader.mesos/mesos/#/frameworks and search for KillrweatherApp to get more info about executors

####Clients

The application contains 2 clients:

1. HTTP client - Rest API, Rest interface on top of Kafka. It can be deployed on the cluster as a marathon service using `killrweatherHTTPClient.json`.
Deploying it as a Marathon service allows to scale it (behind Marathon-LB) to increase scalability and fail over.
KafkaDataIngesterRest.scala is a local client that can communicate with Rest APIs to send weather reports.
2. Google RPC client, Google RPC interface on top of Kafka. It can be deployed on the cluster as a marathon service using `killrweatherGRPCClient.json`.
Deploying it as a Marathon service allows to scale it (behind Marathon-LB) to increase scalability and fail over.
KafkaDataIngesterGRPC.scala is a local client that can communicate with GRPC APIs to send weather reports.



## Loading data
Application can use 3 different loaders:
1 Direct Kafka loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngester` pushes data directly to the Kafka queue
that an application is listening on.
2. HTTP loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngesterRest` writes data to Killrweather HTTP client.
3. GRPC loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngesterGRPC` writes data to Killrweather GRPC client.

## Monitoring and viewing results

Monitoring is done using InfluxDB/Grafana (Grafana definition is in `grafana.json`). For setting up 
Grafana/InfluxDB see this [article](https://mesosphere.com/blog/monitoring-dcos-cadvisor-influxdb-grafana/)
Viewing of the execution results is based on Zeppelin - see current support 
for [Cassandra in Zeppelin](https://zeppelin.apache.org/docs/0.7.0/interpreter/cassandra.html).

To configure Zeppeling for use of Cassandra, make sure that interpreter is configured correctly. The most important ones are:

```

name	                            value
cassandra.cluster	                cassandra
cassandra.hosts	                    node.cassandra.l4lb.thisdcos.directory
cassandra.native.port	            9042

```

A sample notebook can look something like follows

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