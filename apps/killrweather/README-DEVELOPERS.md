# KillrWeather - Developers

These instructions describe how to build KillrWeather and deploy it yourself. To run the prebuilt images in Lightbend's DockerHub account, see the README.md for instructions.

## Build the code

We recommend using [IntelliJ IDEA](https://www.jetbrains.com/idea/) for managing and building the code. The project is organized as several modules:

* `data` - some data files for running the applications, including commands for initializing Cassandra and the actual measurement data
* `diagrams` - original diagrams for overall architecture (now obsolete)
* `killrweather-app` - actual Spark application for data processing - ready to run in Spark. It's used for both local mode and cluster execution. If running locally in IntelliJ, make sure that you use the setting `use classpath of module` for this module
* `killrweather-app-local` - An empty directory that's convenient for running locally when using IntelliJ.
* `killrweather-app-structured` - alternative version of the actual Spark application (leveraging Spark structured streaming) for data processing - ready to run in Spark. It's used for both local mode and cluster execution. If running locally in IntelliJ, make sure that you use the setting `use classpath of module` for this module
* `killrweather-structured-app-local` - An empty directory that's convenient for running locally when using IntelliJ.
* `killrweather-beam` - An experimental [beam](https://beam.apache.org/) version of the same application.
* `killrweatherCore` - some support code used throughout an application. Also includes Embedded Kafka allowing you to run everything locally
* `killrweather-grcpclient` - client for exposing KillrWeather app over [GRPC](https://grpc.io/). This client accepts GRPC messages and publishes them to Kafka for application consumption.
* `killrweather-httpclient` - client for exposing KillrWeather app over HTTP. This client accepts HTTP messages (in JSON) and publishes them to Kafka for application consumption.
* `killrWeather-loader` - a collection of loaders for reading the reports data (from the `data` directory) and publishing it (via Kafka, GRPC, and HTTP) to the application.

## Run Locally on Linux or Mac

For testing convenience, you can run KillrWeather locally on your workstation, although only Linux and Mac are currently supported. It's still easier to connect to running Cassandra and Kafka clusters, as we'll see.

Do the first three steps _if_ you want to run Cassandra locally. If you already have Cassandra running somewhere, skip to step #4, _Run the Application_.

### 1. Download the latest Cassandra

[Download the latest Cassandra](http://cassandra.apache.org/download/) and open the compressed file. For Mac users, you could try using [HomeBrew](https://brew.sh/). However, the 3.11.0 build ("bottle") appears to be misconfigured; the wrong version of the `jamm` library is provided. (You'll see this if you try running one of the start commands below.) Unfortunately, in attempting to refactor HomeBrew modules to add built-in support for installing different versions, it appears to be mostly broken now. Good luck if you can install an earlier version that hopefully works. Otherwise, [download from apache.org](  https://github.com/Homebrew/homebrew-versions) and expand it somewhere convenient.

### 2. Start Cassandra

On Mac, if you successfully installed a working Cassandra distribution using Homebrew, use `brew services start cassandra` if you want to start Cassandra and install it as a service that will always start on reboot. (Use `brew services stop cassandra` to explicitly stop it.) If just want to run Cassandra in a terminal, use `cassandra -f`. This command also works if you just downloaded a build of Cassandra.

### 3. Run the Setup CQL Scripts

Run the setup CQL scripts to create the schema and populate the weather stations table.
On the command line start a cqlsh shell:

    cd /path/to/killrweather/data
    path/to/apache-cassandra-{version}/bin/cqlsh


Run the CQL commands in the [Cassandra Setup](#cassandra-setup) section below.

In the current version, Cassandra setup is done by the application itself, which checks for all Cassandra
prerequisites and set them up, if the manual setup is not done.

### 4. Run the application

The build is done via SBT

    cd killrweather
    sbt compile
    # For IntelliJ users, just import a project and use IntelliJ commands

By default `KillrWeather` defaults to looking for Kafka, Cassandra, and Spark using standard DC/OS or Kubernetes domain names local to each cluster (as appropriate). It also assumes you want to use InfluxDB. So, to run the application locally, you have to pass several flags to the command:

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


Enter `3`.


There are also `-h` and `--help` options that show a help message and exit for each of these commands.

If Running in IntelliJ. Just click on the appropriate classes and run. Any additional parameters can be set by
editing configuration.

### 5.  Killrweather-app-structured

The project also includes the version of the application written using [Spark Structured Streaming](https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html)
This implementation provides functionality similar to the initial one (the main difference is how monthly aggregation is
implemented - due to the limitations of structured streaming implementation, monthly rollup is done not based on the daily rollup, but
rather based on the raw data).
Both applications are sharing the same configurations, so it is not recommended to run them side by side (they will overwrite each other's data)


### Cassandra Setup

KillrWeather automatically setups up the necessary Cassandra tables. Here we show the equivalent CQL commands, using the `CQLSH` shell tool, for your reference.

If you start `CQLSH`, you should see something similar to:

     Connected to Test Cluster at 127.0.0.1:9042.
     [cqlsh {latest.version} | Cassandra {latest.version} | CQL spec {latest.version} | Native protocol {latest.version}]
     Use HELP for help.
     cqlsh>

Running the following scripts would create the tables, etc.:

     cqlsh> source 'create-timeseries.cql';
     cqlsh> source 'load-timeseries.cql';

You can then verify the keyspace was added:

     describe keyspaces;

You can switch the active keyspace context:

     use isd_weather_data ;

You can list out all tables installed:

     describe tables;

The `weather_stations` table should be populated:

     select * from weather_station limit 5;

To close the cql shell:

     cqlsh> quit;

### Cassandra Cleanup

To clean up data in Cassandra, use this command:

     cqlsh> DROP KEYSPACE isd_weather_data;
