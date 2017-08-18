# KillrWeather

KillrWeather is a reference application (which is adopted from Datastax's https://github.com/killrweather/killrweather) showing how to easily leverage and integrate [Apache Spark](http://spark.apache.org),
[Apache Cassandra](http://cassandra.apache.org), and [Apache Kafka](http://kafka.apache.org) for fast, streaming computations in asynchronous. This application focuses on the use case of  **[time series data](https://github.com/killrweather/killrweather/wiki/4.-Time-Series-Data-Model)**.
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

## Start Here
* [KillrWeather Wiki](https://github.com/killrweather/killrweather/wiki) 
* com.datastax.killrweather [Spark, Kafka and Cassandra workers](http://github.com/killrweather/killrweather/tree/master/killrweather-app/src/it/scala/com/datastax/killrweather)


### Build the code 
If this is your first time running SBT, you will be downloading the internet.

    cd killrweather
    sbt compile
    # For IntelliJ users, this creates Intellij project files, but as of
    # version 14x you should not need this, just import a new sbt project.
    sbt gen-idea

### Setup (for Linux & Mac) - 3 Steps
1.[Download the latest Cassandra](http://cassandra.apache.org/download/) and open the compressed file.

2.Start Cassandra - you may need to prepend with sudo, or chown /var/lib/cassandra. On the command line:


    ./apache-cassandra-{version}/bin/cassandra -f

3.Run the setup cql scripts to create the schema and populate the weather stations table.
On the command line start a cqlsh shell:

    cd /path/to/killrweather/data
    path/to/apache-cassandra-{version}/bin/cqlsh

### Setup (for Windows) - 3 Steps
1. [Download the latest Cassandra](http://www.planetcassandra.org/cassandra) and double click the installer.

2. Chose to run the Cassandra automatically during start-up

3. Download the latest version of CQLSH (use PIP)

3. Run the setup cql scripts to create the schema and populate the weather stations table.
On the command line start a `cqlsh` shell:

```
    cd c:/path/to/killrweather
    c:/pat/to/cassandara/bin/cqlsh
    [cassandra-dir]/bin/cqlsh
```

### In CQL Shell:
You should see:

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

### Run
#### Logging
You will see this in all 3 app shells because log4j has been explicitly taken off the classpath:

    log4j:WARN No appenders could be found for logger (kafka.utils.VerifiableProperties).
    log4j:WARN Please initialize the log4j system properly.

What we are really trying to isolate here is what is happening in the apps with regard to the event stream.
You can add log4j locally.

To change any package log levels and see more activity, simply modify
- [logback.xml](http://github.com/killrweather/killrweather/tree/master/killrweather-core/src/resources/logback.xml)

#### From Command Line
1.Start `KillrWeather`

    cd /path/to/killrweather
    sbt app/run

As the `KillrWeather` app initializes, you will see Spark Cluster start, Zookeeper and the Kafka servers start (Application is packaged with in process Kafka, but it can work with external Kafka as well).


2.Start the Kafka data feed app
In a second shell run:

    sbt clients/run

After a few seconds you should see data by entering this in the cqlsh shell:

    cqlsh> select * from isd_weather_data.raw_weather_data;
    cqlsh> select * from daily_aggregate_temperature;
    cqlsh> select * from daily_aggregate_precip;
    

This confirms that data from the ingestion app has published to Kafka, and that both raw and cummulative data is
streaming from Spark to Cassandra from the `KillrWeather` app.

Current implementation only populate daily aggregates. In addition to this, monthly and yearly aggregates can be added 
in a similar fashion

To clean up data in Cassandra, type:

    cqlsh> DROP KEYSPACE isd_weather_data;

To close the cql shell:

    cqlsh> quit;

## DC/OS deployment

Application itself

1. Run sbt 'deploySsh killrWeatherApp'. This will package an uberJar spark.jar and push it to FDP lab
2. Use KillrWeatherApp/src/main/resource/KillrweatherApp.json to run it as a marathon job
4. Use http://killrweatherapp.marathon.mesos:4040/jobs/ to see execution
5. Go to http://leader.mesos/mesos/#/frameworks and search for KillrweatherApp to get more info about executors

Clients

The application contains 3 clients:

1. 



## Future development

The obvious future developments for this applications:
1. Current implementation KafkaDataIngester reads data from file and publish to Kafka. A more realistic implementation should be 
HTTP listener, recieving REST messages and writing them to Kafka
A request to such client will look like


    curl -v -H "Content-Type: application/json" POST http://localhost:5000/weather 
    -d'{"wsid": "wsid", "year": 2008, "month":1, "day": 15, "hour": 3, "temperature": 15, "dewpoint": 10, "pressure": 10, "windDirection": 1, "windSpeed": 2, "skyCondition": 3, "skyConditionText": "blue", "oneHourPrecip": 1.0, "sixHourPrecip": 1.0}'

2. Current implementation includes only daily cummulative data. Both monthly and yearly aggregates can be added to the existing application, 
following the approach implemented here (THe underlying approach here relies on information coming in order. For hourly observations this is quite realistic).
3. Current implementation concentrates on ingestion. Additional data processing can be implemented, based on the data collected to Cassandra.
Current support for [Cassandra in Zeppelin](https://zeppelin.apache.org/docs/0.7.0/interpreter/cassandra.html) makes Zeppelin a great choice for such implementation