# KillrWeather

KillrWeather is a reference application (which is adopted from Datastax's https://github.com/killrweather/killrweather) showing how to easily leverage and integrate [Apache Spark](http://spark.apache.org), [Apache Cassandra](http://cassandra.apache.org), [Apache Kafka](http://kafka.apache.org), [Akka](https://akka.io), and [InfluxDB](https://www.influxdata.com/) for fast, streaming computations. This application focuses on the use case of **[time series data](https://github.com/killrweather/killrweather/wiki/4.-Time-Series-Data-Model)**.

This application also can be viewed as a prototypical IoT (or sensors) data collection application, which stores data in the form of a time series.

## Sample Use Case

_I need fast access to historical data on the fly for predictive modeling with real time data from the stream._

The application does not quite do that, it stops at capturing real-time and cumulative information.

## Reference Application

[KillrWeather Main App](https://github.com/killrweather/killrweather/tree/master/killrweather-app/src/main/scala/com/datastax/killrweather) is the entry point.

## Time Series Data

The use of time series data for business analysis is not new. What is new is the ability to collect and analyze massive volumes of data in sequence at extremely high velocity to get the clearest picture to predict and forecast future market changes, user behavior, environmental conditions, resource consumption, health trends and much, much more.

Apache Cassandra is a NoSQL database platform particularly suited for these types of Big Data challenges. Cassandra’s data model is an excellent fit for handling data in sequence regardless of data type or size. When writing data to Cassandra, data is sorted and written sequentially to disk. When retrieving data by row key and then by range, you get a fast and efficient access pattern due to minimal disk seeks – time series data is an excellent fit for this type of pattern. Apache Cassandra allows businesses to identify meaningful characteristics in their time series data as fast as possible to make clear decisions about expected future outcomes.

There are many flavors of time series data. Some are windows into the stream, others cannot be windows, because the queries are not by time slice but by specific year, month, day, hour, etc. Spark Streaming lets you do both.

In addition to Cassandra, the application also demonstrates integration with InfluxDB and Grafana. This toolset is typically used for DevOps monitoring purposes, but it can also be very useful for real-time visualization of stream processing. Data is written to influxDB as it gets ingested in the application and Grafana is used to provide a real time view of temperature, pressure, and dewpoint values, as the reports come in. Additionally, the application provides results of data rollups - daily and monthly mean, low, and high values.

## Start Here

The original [KillrWeather Wiki](https://github.com/killrweather/killrweather/wiki) is still a great source of information.

## Build the code

We recommend using [IntelliJ IDEA](https://www.jetbrains.com/idea/) for managing and building the code. The project is organized as several modules:

* `data` - some data files for running the applications, including commands for initializing Cassandra and the actual measurement data
* `diagrams` - original diagrams for overall architecture (now obsolete)
* `killrweather-app` - actual Spark application for data processing - ready to run in Spark. It's used for both local mode and cluster execution. If running locally in IntelliJ, make sure that you use the setting `use classpath of module` for this module
* `killrweather-app-local` - An empty directory that's convenient for running locally when using IntelliJ.
* `killrweatherCore` - some support code used throughout an application. Also includes Embedded Kafka allowing you to run everything locally
* `killrweather-grcpclient` - client for exposing KillrWeather app over [GRPC](https://grpc.io/). This client accepts GRPC messages and publishes them to Kafka for application consumption.
* `killrweather-httpclient` - client for exposing KillrWeather app over HTTP. This client accepts HTTP messages (in JSON) and publishes them to Kafka for application consumption.
* `killrWeather-loader` - a collection of loaders for reading the reports data (from the `data` directory) and publishing it (via Kafka, GRPC, and HTTP) to the application.

The build is done via SBT

    cd killrweather
    sbt compile
    # For IntelliJ users, just import a project and use IntelliJ commands


## Run Locally on Linux or Mac

For testing convenience, you can run KillrWeather locally on your workstation, although only Linux and Mac are currently supported. It's still easier to connect to running Cassandra and Kafka clusters, as we'll see.

Do the first three steps _if_ you want to run Cassandra locally. If you already have Cassandra running somewhere, skip to step #4, _Run the Application_.

#### 1. Download the latest Cassandra

[Download the latest Cassandra](http://cassandra.apache.org/download/) and open the compressed file. For Mac users, you could try using [HomeBrew](https://brew.sh/). However, the 3.11.0 build ("bottle") appears to be misconfigured; the wrong version of the `jamm` library is provided. (You'll see this if you try running one of the start commands below.) Unfortunately, in attempting to refactor HomeBrew modules to add built-in support for installing different versions, it appears to be mostly broken now. Good luck if you can install an earlier version that hopefully works. Otherwise, [download from apache.org](  https://github.com/Homebrew/homebrew-versions) and expand it somewhere convenient.

#### 2.Start Cassandra

On Mac, if you successfully installed a working Cassandra distribution using Homebrew, use `brew services start cassandra` if you want to start Cassandra and install it as a service that will always start on reboot. (Use `brew services stop cassandra` to explicitly stop it.) If just want to run Cassandra in a terminal, use `cassandra -f`. This command also works if you just downloaded a build of Cassandra.

#### 3. Run the Setup CQL Scripts

Run the setup CQL scripts to create the schema and populate the weather stations table.
On the command line start a cqlsh shell:

    cd /path/to/killrweather/data
    path/to/apache-cassandra-{version}/bin/cqlsh

Run the CQL commands in the [Cassandra Setup](#cassandra-setup) section below.

#### 4. Run the application

By default `KillrWeather` defaults to looking for Kafka, Cassandra, and Spark using standard DC/OS domain names local to each cluster. It also assumes you want to use InfluxDB. So, to run the application locally, you have to pass several flags to the command:

* `--master local[*]`: You need to specify the Spark master.
* `--without-influxdb`: so it doesn't try to write to InfluxDB, which is the default behavior. (For "symmetry", there is a `--with-influxdb` flag, too, but it's the default.)
* `--kafka-brokers kbs`: If running Kafka locally, then `localhost:9092` is probably right for the `kbs` host and port value. If running elsewhere in a cluster, then use the appropriate `ip:port` (or comma-separated list of them).
* `--cassandra-hosts chs`: If running Kafka locally, then `localhost` is right for `chs`.

At the SBT prompt, use this command sequence to switch to the `killrWeatherApp` subproject and run `com.lightbend.killrweather.app.KillrWeather`. Here we'll use IP addresses in a fictitious cluster that are routable from the workstation running SBT:

```bash
project killrWeatherApp
run --master local[*] --without-influxdb --kafka-brokers 10.8.0.10:1025 --cassandra-hosts 10.8.0.33
```

There is only one "main" class in the app project, `...KillrWeather`, so SBT's `run` tasks finds and runs it.

> If you want to run these commands from `bash`, quote as follows:
>
>```
> sbt "project killrWeatherApp" "run --master local[*] --without-influxdb ..."
>```


You should see lots of log messages printed to the console, then a sequence of Spark Streaming diagnostic headers like the following:

```
-------------------------------------------
Time: 1505870840000 ms
-------------------------------------------

-------------------------------------------
Time: 1505870845000 ms
-------------------------------------------
```

Note there will be no data written between these "banners", because there is no data flowing into the system yet. To get data, we need to run one of the "loaders".

There are several "mains" for project `loader`, so we use `runMain`:

```
project loader
runMain com.lightbend.killrweather.loader.kafka.KafkaDataIngester --kafka-brokers 10.8.0.10:1025
```

(You could pass all the same arguments we passed to `KillrWeather`, but only the `--kafka-brokers` argument is useful. You'll notice invalid, default values printed to the console for the Cassandra Hosts, etc., which you can ignore.)

Actually, you can you just `run` with the arguments and you'll be prompted for which `ingester` to run:

```
run --kafka-brokers 10.8.0.10:1025
[warn] Multiple main classes detected.  Run 'show discoveredMainClasses' to see the list

Multiple main classes detected, select one to run:

 [1] com.lightbend.killrweather.loader.grpc.KafkaDataIngesterGRPC
 [2] com.lightbend.killrweather.loader.http.KafkaDataIngesterRest
 [3] com.lightbend.killrweather.loader.kafka.KafkaDataIngester
 [4] com.lightbend.killrweather.loader.utils.test.TestFilesIterator

Enter number:
```

Enter `3`.

There are also `-h` and `--help` options that show a help message and exit for each of these commands.

TODO: RUNNING IN IntelliJ


### Cassandra Setup

Use these CQL commands whether running Cassandra locally or in a cluster.

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


## FDP DC/OS Deployment

Now let's see how to install KillrWeather completely in an FDP DC/OS Cluster.

### Prerequisites

A few services must be installed in the cluster first.

#### 1. Install Required Services

To run KillrWeather in an FDP Cluster, you'll need to start by installing the services it needs.

If not already installed, install our Kafka distribution, InfluxDB using [this GitHub repo](https://github.com/typesafehub/fdp-influxdb-docker-images), and use the Universe/Catalog to install Grafana and Cassandra. More information about InfluxDB and Grafana is provided below in _Monitoring and Viewing Results_

After installing Cassandra, run the commands above in _Cassandra Setup_.

#### 2. Install jim-lab

Install `jim-lab`. TODO: REPLACE WITH THE SAMPLE APPS VERSION.

### Build and Deploy the Application Archives

The SBT build uses a [sbt-deploy-ssh](https://github.com/shmishleniy/sbt-deploy-ssh) plugin and a `/.deploy.conf` file with configuration information to copy the "uber jars" for the application to a web server provided by the `jim-lab` application container. The configuration of the plugin is defined in `./projects/Settings.scala`.

#### 3. Set Up deploy.conf

In what follows, more details of deploying to FDP-Lab and submitting the apps to Marathon are described [here](https://docs.google.com/document/d/1eMG8I4z6mQ0C4Llg1VHnpV7isnVAtnk-pOkDo8tIubI/edit#heading=h.izl4k6rmh4c0).
TODO: remove this link before distribution. Add any additional useful bits from that document here first.

Copy `./deploy.conf.template` to `./deploy.conf` and edit the settings if necessary. However, they are already correct for `jim-lab`. (Use `jim-lab` if you deployed the default `fdp-laboratory-base` image.) Here is the default configuration:

```json
servers = [
  {
    name = "killrWeather"
    user = "publisher"
    host = jim-lab.marathon.mesos
    port = 9022
    sshKeyFile = "id_rsa"
  }
]
```

* `name`: the name used for identifying the configuration.
* `user`: the FDP user inside the container for ssh command.
* `host`: the Mesos name for the laboratory service.
* `port`: the ssh port (by default laboratory uses port 9022).
* `sshKeyFile`: the file used by the laboratory configuration to authenticate the user (should not have a password).

#### 4. Deploy the Application to FDP Laboratory

Run the following SBT command:

```bash
sbt 'deploySsh killrWeather'
```

This will create an uber jar called `killrweather-spark.jar` and copy it to the FDP "laboratory" container, directory `/var/www/html`, from where it can be served through HTTP to other nodes as you submit the apps to Marathon.

#### 5. Run the Main Application with Marathon

The JSON file used to configure the app is `KillrWeather-app/src/main/resource/KillrweatherApp.json`. To run the app as a marathon job, use the following DC/OS CLI command:

```bash
dcos marathon app add < killrweather-app/src/main/resource/killrweatherApp.json
```

(Note the redirection of input.)

This starts the app running, which is a Spark Streaming app. We won't show the contents of the JSON file here, but it's a good example of running a Spark job with Marathon, where the application jar is served using a web server, the one inside `jim-lab`.

#### 6. See What's Going On...

Go to the Spark Web console for this job at http://killrweatherapp.marathon.mesos:4040/jobs/ to see the minibatch and other jobs that are executed as part of this Spark Streaming job.

Go to http://leader.mesos/mesos/#/frameworks and search for KillrweatherApp to get more info about the corresponding executors.

#### 7. Deploy the Clients

The application contains two clients, one for HTTP (`httpclient-1.0.1-SNAPSHOT.tgz`) and one for GRPC (`grpcclient-1.0.1-SNAPSHOT.tgz`). Deploying either one as a Marathon service allows it to be scaled easily (behind Marathon-LB) to increase scalability and failover. Both archives were also deployed to `jim-lab`.

1. The HTTP client uses the REST API on top of Kafka. It can be deployed on the cluster as a marathon service using the command:

```bash
dcos marathon app add < ./killrweather-httpclient/src/main/resources/killrweatherHTTPClient.json
```

2. The Google RPC client uses the Google RPC interface on top of Kafka. It can be deployed on the cluster as a marathon service using the following command:

```bash
dcos marathon app add < ./killrweather-grpcclient/src/main/resources/killrweatherGRPCClient.json
```


### Loading data

The application can use three different loaders, which are local client applications that can communicate with the corresponding cluster services or clients to send weather reports.:

1. Direct Kafka loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngester` pushes data directly to the Kafka queue
that an application is listening on.
2. HTTP loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngesterRest` writes data to KillrWeather HTTP client.
3. GRPC loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngesterGRPC` writes data to KillrWeather GRPC client.

Use the commands we saw previously for data loading to run these commands locally.

### Monitoring and Viewing Results

For information about setting up Grafana and InfluxDB, see this [article](https://mesosphere.com/blog/monitoring-dcos-cadvisor-influxdb-grafana/).

Monitoring is done using InfluxDB and Grafana. In the Grafana UI, load the definitions in `./killrweather-app/src/main/resource/grafana.json`. (Click the upper-left-hand side Grafana icon, then _Dashboards_, then _Import_.) This will create a dashboard called _KillrWeather Data Ingestion_.

Once set up and once data is flowing through the system, you can view activity in this dashboard.

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

