# KillrWeather - Developers

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

### 2.Start Cassandra

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

Use these CQL commands whether running Cassandra locally or in a cluster. This is only required if you want to see how this is done.
Applications will now do this setup on startup.

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

To close the cql shell:

     cqlsh> quit;

### Cassandra Cleanup

To clean up data in Cassandra, start `CQLSH` and type:


     cqlsh> DROP KEYSPACE isd_weather_data;

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

This starts the app running, which is a Spark Streaming app. We won't show the contents of the JSON file here, but it's a good example of running a Spark job with Marathon, 
where the application jar is served using a web server, the one inside `jim-lab`.

### Deploy the Clients

The application contains two clients, one for HTTP (`httpclient-1.0.1-SNAPSHOT.tgz`) and one for GRPC (`grpcclient-1.0.1-SNAPSHOT.tgz`). Deploying either one as a Marathon service allows it to be scaled easily (behind Marathon-LB) to increase scalability and failover. Both archives were also deployed to `jim-lab`.

1. The HTTP client uses the REST API on top of Kafka. It can be deployed on the cluster as a marathon service using the command:

```bash
dcos marathon app add < ./killrweather-httpclient/src/main/resources/killrweatherHTTPClient.json
```

2. The Google RPC client uses the Google RPC interface on top of Kafka. It can be deployed on the cluster as a marathon service using the following command:

```bash
dcos marathon app add < ./killrweather-grpcclient/src/main/resources/killrweatherGRPCClient.json
```

