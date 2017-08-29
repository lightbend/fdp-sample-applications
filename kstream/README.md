# Kafka Streams Sample App for Weblog Processing

This project consists of 2 applications that demonstrate the use of Kafka Streams.  Both apps ingest sample web log data from the [Clarknet dataset](http://ita.ee.lbl.gov/html/contrib/ClarkNet-HTTP.html), which is described below.

> These two traces contain two week's worth of all HTTP requests to the ClarkNet WWW server. ClarkNet is a full Internet access provider for the Metro Baltimore-Washington DC area.

Sample Applications:

* *WeblogProcessing* - An implementation of the of the Kafka Streams DSL.  Bundled using the `dslPackage` SBT submodule.
* *WeblogDriver* - An implementation of the lower level Kafka Processor API.  Bundled using the `procPackage` SBT
submodule.

Together these samples demonstrate the following features of Kafka Streams:

1. Building and configuring a Streams based topology using Kafka Streams DSL as well as the lower level processor based APIs
2. Transformation semantics applied to streams data
3. Stateful transformation using *local* state stores
4. Interactive queries in Kafka streams applied to a distributed application
5. Implementing *custom* state stores
6. Interactive queries over custom state stores in a distributed setting

## Installing the Applications

The applications can be installed from the `bin` folder of the project root. Here's a summary of all the options available for the installation script.

```bash
$ pwd
<project root>
$ cd bin
$ ./app-install.sh --help
  Installs the Kafka Streams sample application. Assumes DC/OS authentication was successful
  using the DC/OS CLI.

  Usage: app-install.sh   [options]

  eg: ./app-install.sh

  Options:
  --config-file        Configuration file used to launch applications
                       Default: ./app-install.properties
  --start-only X       Only start the following apps:
                         dsl         Starts topology based on Kafka Streams DSL
                         procedure   Starts topology that implements custom state repository based on Kafka Streams procedures
                       Repeat the option to run more than one.
                       Default: runs all of them
  -n | --no-exec       Do not actually run commands, just print them (for debugging).
  -h | --help          Prints this message.
```

Some of the valid options to install the applications are:

```bash
$ ./app-install.sh --start-only dsl
```

This will install the DSL based module as one of the applications running under Marathon in the DC/OS cluster.

```bash
$ ./app-install.sh --start-only procedure
```

This will install the lower level procedure based module as one of the applications running under Marathon in the DC/OS cluster.

```bash
$ ./app-install.sh
```

This will install both the modules as applications running under Marathon in the DC/OS cluster.

**If you decide to install and run both the applications together, please ensure your Kafka cluster is beefy enough to handle the load.**

### Installation configuration file

The default configuration information is from `app-install.properties`, which has the following format:

```bash
## dcos kafka package - valid values : kafka | confluent-kafka
kafka-dcos-package=confluent-kafka

## whether to skip creation of kafka topics - valid values : true | false
skip-create-topics=false

## kafka topic partition : default 1
kafka-topic-partitions=1

## kafka topic replication factor : default 1
kafka-topic-replication-factor=1

## name of the user used to publish the artifact.  Typically 'publisher'
publish-user="publisher"

## the IP address of the publish machine
publish-host="10.8.0.14"

## port for the SSH connection. The default configuration is 9022
ssh-port=9022

## passphrase for your SSH key.  Remove this entry if you don't need a passphrase
passphrase=

## the key file in ~/.ssh/ that is to be used to connect to the deployment host
ssh-keyfile="dg-test-fdp.pem"

## the folder where data for ingestion will be put
ingestion-directory=/tmp/data

## laboratory mesos deployment
laboratory-mesos-path=http://jim-lab.marathon.mesos
```

A few of the parameters relate to the configuration of `fdp-laboratory`, which is the basic engine for installing all sample applications of FDP.

However using `--config-file` option, you can specify your own configuration file as well.

### Starting the data ingestion process

Once the applications are installed as Marathon services, here's how to kickstart the data ingestion process for each module. This ingestion will actually start the streams topology and the application itself.

1. Data needed to run the application have been packaged as part of the installation. Data downloaded from the source site is made available in the folder specified under `ingestion-directory` in the configuration file.
2. Login to the node where the application is running and `touch` the file available under that folder to kickstart the ingestion process.
3. This needs to be done for every module deployed


## Application REST API's

Both examples run a self-contained http server that can be used to query information from the state stores used in the
Kafka Streams pipelines.  You can call these API's to determine if the sample applications have are running correctly.

### DSL based module

This module demonstrates stateful streaming using local state stores. These state stores can be queried using interactive queries of Kafka streams through the http service developed as part of this application. The ingested data consists of http access logs of a number of URLs - this module supports 2 types of queries on the application state:

1. count of the number of accesses made to a specific host
2. count of the number of bytes transferred to a specific host

Here's how to query using http interface. We are using `curl` for demonstration here:

```bash
$ curl http://10.8.0.9:7070/weblog/access/world.std.com
```

This reports the number of accesses made to the host `world.std.com`.

```bash
$ curl http://10.8.0.9:7070/weblog/bytes/world.std.com
```

This reports the number of bytes transferred to the host `world.std.com`.

### Procedure based Custom State Store Module

This module demonstrates the use of custom state stores. This module has implemented a state store based on the Bloom Filter data structure. The purpose is to store membership information in a sublinear data structure. And this module offers interactive queries on top of this custom store. The query returns true or false depending on whether the data for the queried host has been stored or not.

Here's how to query using http interface. We are using `curl` for demonstration here:

```bash
$ curl http://10.8.0.9:7070/weblog/access/check/world.std.com
```

This reports `true` if the host `world.std.com` has been seen in the ingested data. Else it returns `false`.

> *Note:* When deployed on the cluster, Marathon assigns random free ports to both the applications. Check the log file (`kstream-dsl.log` or `kstream-proc.log`) for the assigned port number. The log files can be found from the Mesos console by clicking into the Mesos Task corresponding to the application. Go to `http://<Master URL>/mesos` and click on the appropriate task's Sandbox link. Move to the `logs` folder and look for the pattern **REST endpoint at http://0.0.0.0:25961** in the `kstream-*.log` file. In this example, `25961` is the assigned port number.

## Running in Distributed mode

Both the DSL based and Procedure based applications can be run in distributed mode. Multiple instances of the application can be run and all state can be queried using any of the host/port combination. Here are some things that need to be taken care of when running any of the applications in the distributed mode:

* The application `id` in the Marathon deployment json must be different for each of the instances. The typical way to ensure this is to use the given `app-install.sh` for installing one instance and then copying the deployment json and changing it for subsequent deployment instances.
* Some of the settings in the `cmd`, `env` and `uris` section of the deployment json need to be different for subsequent instances. Please refer to `kstream-app-dsl-subsequent-instances.json.template` or `kstream-app-proc-subsequent-instances.json.template` for the details of such changes.
* In order to run multiple instances, ensure that all Kafka topics are created with number of partitions > 1 (>= the number of instances run) with replication factors >= 1. Both these factors can be supplied in the `app-install.properties` file during installation.

## Application Restarts

Kafka streams manages state of the application for stateful streaming in local stores. For resiliency these stores are also backed up by Kafka topics, known as *changelog topics*. In case of application restarts, if it happens that the application is restarted ina different node of the cluster, Kafka will recreate the state store based on the compacted information of the changelog topic. This is also useful if the application faces any exception and Mesos does a restart albeit in a different node.

> The following sections describe the application architecture and how the project builds and packaging are done. Feel free to skip these in case you just want to deploy and install the default configurations.

## Kafka Streams' DSL based module

This application module consists of the following components:

1. The application assumes that source data will be injected into the source topic by an external data loading program.
2. A driver application that streams data from the Kafka topic loaded in (1), does stateful transformations on the stream data, keeps application state updated in state stores and as well writes to destination topics
3. A microservice with http endpoints that allows users to query from Kafka Streams state stores.

The state stores populated by Kafka streams are local to the instance of stream running. Hence in a distributed mode (multiple instances of the streams application running), we need to have the proper infrstructure in place to fetch meaningful results from the state stores. This is the feature of [Interactive Queries](http://docs.confluent.io/current/streams/developer-guide.html#id8) supported by Kafka Streams, where for some applications you may implement a lightweight query infrastructure for fetching necessary application state information from the state stores.

The important point to note here is that the full infrastructure of querying from your application is not available out-of-the-box from Kafka Streams implementation. From the documentation:

> Interactive queries allow you to tap into the state of your application, and notably to do that from outside your application. However, an application is not interactively queryable out of the box: you make it queryable by leveraging the API of Kafka Streams.

This sample application does an implementation of this infrastructure by exposing http end-points for the query service. The following section describe how to use these end-points along with running the driver application.

### Application running mode in Kafka Streams

Consider a Kafka Streams application that consumes from n topics, each with p partitions. If the application starts on a single machine with number of threads configured to t, then Kafka Streams will prepare its default topology consisting of the topic partitions, instance threads and a set of tasks. The details of how this gets set up is described in this [document on Parallelism Model from Confluent](http://docs.confluent.io/current/streams/architecture.html#streams-architecture-parallelism-model).

Now if we want to scale out this application to another node with a single thread, a new thread will be created, and input partitions will be re-assigned leading to migration of some of the parttions, their tasks and their state stores to the new machine. The complete state of the application is now distributed across the 2 machines making querying of the state difficult.

In this application we develop an http based service that queries all *necessary* nodes for the state information to serve the user.

### The Driver Program of the application

The driver program is the object `WeblogProcessing` that is a plain Scala application with a `main` function. This program assumes that data will be available for streaming in the Kafka topic specified by `dcos.kafka.fromtopic`. The next section describes how you can ingest data into this topic. The full config of the application consists of the following key/value pairs:

```
akka {
  loglevel = INFO
  log-config-on-start = on
  loggers = ["akka.event.slf4j.Slf4jLogger"]
  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
  event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
}

dcos {

  kafka {
    ## bootstrap servers for Kafka
    brokers = "localhost:9092"
    brokers = ${?KAFKA_BROKERS}

    ## consumer group
    group = "group-dsl"
    group = ${?KAFKA_GROUP_DSL}

    ## the source topic - processing starts with
    ## data in this topic (to be loaded by ingestion)
    fromtopic = "server-log-dsl"
    fromtopic = ${?KAFKA_FROM_TOPIC_DSL}

    ## processed records goes here in json of LogRecord
    totopic = "processed-log"
    totopic = ${?KAFKA_TO_TOPIC_DSL}

    ## this gets the avro serialized data from totopic for processing by Kafka Connect
    ## HDFS sink connector
    avrotopic = "avro-topic"
    avrotopic = ${?KAFKA_AVRO_TOPIC_DSL}

    ## summary access information gets pushed here
    summaryaccesstopic = "summary-access-log"
    summaryaccesstopic = ${?KAFKA_SUMMARY_ACCESS_TOPIC_DSL}

    ## windowed summary access information gets pushed here
    windowedsummaryaccesstopic = "windowed-summary-access-log"
    windowedsummaryaccesstopic = ${?KAFKA_WINDOWED_SUMMARY_ACCESS_TOPIC_DSL}

    ## summary payload information gets pushed here
    summarypayloadtopic = "summary-payload-log"
    summarypayloadtopic = ${?KAFKA_SUMMARY_PAYLOAD_TOPIC_DSL}

    ## windowed summary payload information gets pushed here
    windowedsummarypayloadtopic = "windowed-summary-payload-log"
    windowedsummarypayloadtopic = ${?KAFKA_WINDOWED_SUMMARY_PAYLOAD_TOPIC_DSL}

    ## error topic for the initial processing
    errortopic = "logerr-dsl"
    errortopic = ${?KAFKA_ERROR_TOPIC_DSL}

    zookeeper = "localhost:2181"
    zookeeper = ${?ZOOKEEPER_URL}

    schemaregistryurl = "http://localhost:8081"
    schemaregistryurl = ${?SCHEMA_REGISTRY_URL}

    ## folder where state stores are created by Kafka Streams
    statestoredir = "/tmp/kafka-streams"
    statestoredir = ${?STATESTOREDIR}

    ## settings for data ingestion
    loader {
      sourcetopic = ${dcos.kafka.fromtopic}
      sourcetopic = ${?KAFKA_FROM_TOPIC_DSL}

      directorytowatch = "/Users/debasishghosh/t"
      directorytowatch = ${?DIRECTORY_TO_WATCH}

      pollinterval = 1 second
    }
  }

  # http endpoints of the weblog microservice
  http {
    # The port the dashboard listens on
    port = 7070
    port = ${?PORT_DSL}

    # The interface the dashboard listens on
    interface = "localhost"
    interface = ${?INTERFACE_DSL}
  }
}
```

The input records are assumed to be of the following format:

```
access9.accsyst.com - - [28/Aug/1995:00:00:35 -0400] "GET /pub/robert/curr99.gif HTTP/1.0" 200 5836
world.std.com - - [28/Aug/1995:00:00:36 -0400] "GET /pub/atomicbk/catalog/sleazbk.html HTTP/1.0" 200 18338
cssu24.cs.ust.hk - - [28/Aug/1995:00:00:36 -0400] "GET /pub/job/vk/view17.jpg HTTP/1.0" 200 5944
```

The program then does the following:

1. Parses each record and creates an instance of `LogRecord`, which it then persists in another Kafka
topic (`dcos.kafka.totopic`). If there's any exception processing a record, that record goes to the error topic
(`dcos.kafka.errortopic`).
2. Transforms and creates summary information into Kafka KTables.
3. It creates 2 types of summary information:
  * Report summary information of the number of times each host has been accessed. The steps followed are:
       * Transforms input stream into one containing the host name
       * Maps on the stream to change the key to host-name, so we now have a (hostname, hostname) tuple in the stream
       * Does a `groupByKey` followed by a `count` to form a `KTable` as a result of a stateful transformation
       * Materializes the `KTable` into a Kafka topic
  * Report summary information of the hostwise payload size processed by the server. The steps followed are:
       * Transforms input stream into one containing the host name and payload size
       * Maps on the stream to change the key to host-name, so we now have a (hostname, payload-size) tuple in the stream
       * Does a `groupByKey` followed by an `aggregate` to form a `KTable` as a result of a stateful transformation
       * Materializes the `KTable` into a Kafka topic
4. Sets up a REST microservice that offers query for the state store `WeblogDSLHttpService`.

### Ingesting Data

In order for the application to run, we need to ingest data into the source topic (`dcos.kafka.fromtopic`). This can be done using a file watcher based implementation. The `dcos.kafka.loader` section of the configuration file describes the settings of the file watcher. As a user you need to set up the appropriate folder name in `directorytowatch` - the application pulls data from this folder and loads into the topic.


### How to package the application

The driver application is packaged using `sbt-native-packager`. The details of the packaging is in `build.sbt`. To build the package, use the following steps starting from the project root:

```
$ sbt
> dslPackage/universal:packageZipTarball
```

This will create a `tgz` of the application package for the module in a folder `$PROJECT_ROOT/build/dsl/target/universal`.

```
$ cd build/dsl/target/universal
$ tar xvfz dslpackage-0.1.tgz
```

This creates the distribution under the folder `dslpackage-0.1`. The folder has 3 subfolders:

* `bin` containing the startup scripts
* `conf` containing the configuration file `application.conf` and the logging configuration `logback.xml`
* `lib` containing all the jars

The configuration fies in `conf` can be customized for the deployment environment.

Here's how the application can be run using the generated startup script:

```
$ pwd
<project root>/build/dsl/target/universal
$ bin/dslpackage
```

Before the application can be run, the kafka topics need to be created. Here are some sample commands for doing the same:

```
$ $KAFKA_HOME/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic logerr
$ $KAFKA_HOME/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic server-log
$ $KAFKA_HOME/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic processed-log
$ $KAFKA_HOME/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic summary-access-log
$ $KAFKA_HOME/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic summary-payload-log
```

In order for the application to run, data need to be loaded in the input topic (`server-log-dsl` in the above example). This can be done either through a bash script or through a data loading application in case of a Mesos DC/OS clustered application. We discuss the latter later in the document.


### Deploy on FDP cluster

We will use [fdp-laboratory-base](https://github.com/typesafehub/fdp-laboratory.git) to deploy the application as a Marathon job on the FDP cluster. The application build uses a plugin `sbt-deploy-ssh` which does the plumbing of remote deploy. See `build.sbt` for details. Here are the steps to follow for a remote deploy of the service:

```
$ sbt
> dslPackage/deploySsh fdp-kstream-dsl
```
This will do the deploy of the `tgz` in `/var/www/html/` of the remote server. The specifications of the remote server are given in a file `deploy.conf` which should be in the project root folder. Here's how `deploy.conf` looks like:

```
servers = [
 {
  name = "fdp-kstream-dsl"
  user = "publisher"
  host = "52.173.78.24"
  port = 9022
  sshKeyFile = "dg-test-fdp.pem"
 },
 {
  name = "fdp-kstream-proc"
  user = "publisher"
  host = "52.173.78.24"
  port = 9022
  sshKeyFile = "dg-test-fdp.pem"
 }
]
```

This file contains the parameters for both the `dsl` and the `proc` packages. We will discuss the `proc` package later in this document.

Once the `deploySsh` is complete we can run the application as a Marathon job. The project root folder contains a json file `fdp-kstream-dsl.json` that contains all the relevant settings for deployment. Here's the file contents:

```json
{
  "id": "/kstream-app-dsl",
  "cmd": "mkdir -p /tmp/data && mv clarknet_access_log_Aug28 /tmp/data && chmod 777 /tmp/data/clarknet_access_log_Aug28 && export PATH=$(ls -d $MESOS_SANDBOX/jre*/bin):$PATH && ./dslpackage-0.1/bin/dslpackage",
  "env": {
    "INTERFACE_DSL": "0.0.0.0",
    "KAFKA_BROKERS": "broker-0.confluent-kafka.mesos:9301",
    "DIRECTORY_TO_WATCH": "/tmp/data",
    "KAFKA_FROM_TOPIC_DSL": "server-log-dsl",
    "KAFKA_TO_TOPIC_DSL": "processed-log",
    "KAFKA_AVRO_TOPIC_DSL": "avro-topic",
    "KAFKA_SUMMARY_ACCESS_TOPIC_DSL": "summary-access-log",
    "KAFKA_WINDOWED_SUMMARY_ACCESS_TOPIC_DSL": "windowed-summary-access-log",
    "KAFKA_SUMMARY_PAYLOAD_TOPIC_DSL": "summary-payload-log",
    "KAFKA_WINDOWED_SUMMARY_PAYLOAD_TOPIC_DSL": "windowed-summary-payload-log",
    "KAFKA_ERROR_TOPIC_DSL": "logerr-dsl",
    "SCHEMA_REGISTRY_URL": "http://10.8.0.11:16568"
  },
  "cpus": 1.0,
  "mem": 8192,
  "disk": 20480,
  "instances": 1,
  "fetch": [
    { "uri": "https://downloads.mesosphere.com/java/jre-8u112-linux-x64.tar.gz" },
    { "uri": "http://jim-lab.marathon.mesos/dslpackage-0.1.tgz" },
    { "uri": "ftp://ita.ee.lbl.gov/traces/clarknet_access_log_Aug28.gz" }
  ]
}
```

*Note the file sets the environment variables `$INTERFACE_DSL` that sets the host  of deployment for the http endpoint of the REST microservice. This variable is picked up by the configuration parameter `dcos.http.interface`. For the port number of the endpoint a random free port will be allocated if the one allocated by the application is not free. The value of this port number can be found from the application log file.*

## Kafka Streams' Procedure API based module

This application module consists of the following components:

1. The application assumes that source data will be injected into the source topic by an external data loading program.
2. A driver application that streams data from the Kafka topic loaded in (1), does stateful transformations on the stream data, keeps application state updated in a *custom* state store.
3. The main difference with the DSL based version is that this module uses the Processor APIs of Kafka Streams to build the topology. Also the state store is a custom one - a Bloom Filter based storage that checks for membership of the passed in key.
4. A microservice with http endpoints that allows users to query from Kafka Streams state stores.

### The Driver Program of the application

The driver program is the object `WeblogDriver` that is a plain Scala application with a `main` function. This program assumes that data will be available for streaming in the Kafka topic specified by `dcos.kafka.fromtopic`. Data ingestion can be done using the same procedure as outlined in the DSL section above. The full config of the application consists of the following key/value pairs:

```
akka {
  loglevel = INFO
  log-config-on-start = on
  loggers = ["akka.event.slf4j.Slf4jLogger"]
  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
  event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
}

dcos {

  kafka {
    ## bootstrap servers for Kafka
    brokers = "localhost:9092"
    brokers = ${?KAFKA_BROKERS}

    ## consumer group
    group = "group-proc"
    group = ${?KAFKA_GROUP_PROC}

    ## the source topic - processing starts with
    ## data in this topic (to be loaded by ingestion)
    fromtopic = "server-log-proc"
    fromtopic = ${?KAFKA_FROM_TOPIC_PROC}

    ## error topic for the initial processing
    errortopic = "logerr-proc"
    errortopic = ${?KAFKA_ERROR_TOPIC_PROC}

    zookeeper = "localhost:2181"
    zookeeper = ${?ZOOKEEPER_URL}

    ## folder where state stores are created by Kafka Streams
    statestoredir = "/tmp/kafka-streams"
    statestoredir = ${?STATESTOREDIR}

    ## settings for data ingestion
    loader {
      sourcetopic = ${dcos.kafka.fromtopic}
      sourcetopic = ${?KAFKA_FROM_TOPIC_PROC}

      directorytowatch = "/Users/debasishghosh/t"
      directorytowatch = ${?DIRECTORY_TO_WATCH}

      pollinterval = 1 second
    }
  }

  # http endpoints of the weblog microservice
  http {
    # The port the dashboard listens on
    port = 7071
    port = ${?PORT_PROC}

    # The interface the dashboard listens on
    interface = "localhost"
    interface = ${?INTERFACE_PROC}
  }
}
```

The input records are assumed to be of the following format:

```
access9.accsyst.com - - [28/Aug/1995:00:00:35 -0400] "GET /pub/robert/curr99.gif HTTP/1.0" 200 5836
world.std.com - - [28/Aug/1995:00:00:36 -0400] "GET /pub/atomicbk/catalog/sleazbk.html HTTP/1.0" 200 18338
cssu24.cs.ust.hk - - [28/Aug/1995:00:00:36 -0400] "GET /pub/job/vk/view17.jpg HTTP/1.0" 200 5944
```

The program then does the following:

1. Parses each record and creates an instance of `LogRecord` using the Processor based API.
2. The processed record is passed on to the bloom filter based store for storage. The bulk of the code in this module is in creating and managing the custom state store.
3. Sets up a REST microservice that offers query for the state store to check the membership of the passed in key.


### How to package and run the driver application

The driver application is packaged using `sbt-native-packager`. The details of the packaging is in `build.sbt`. To build the package, use the following steps starting from the project root:

```
$ sbt
> procPackage/universal:packageZipTarball
```

This will create a `tgz` of the application package for the module in a folder `$PROJECT_ROOT/build/proc/target/universal`.

```
$ cd build/proc/target/universal
$ tar xvfz procpackage-0.1.tgz
```

This creates the distribution under the folder `procpackage-0.1`. The folder has 3 subfolders:

* `bin` containing the startup scripts
* `conf` containing the configuration file `application.conf` and the logging configuration `logback.xml`
* `lib` containing all the jars

The configuration fies in `conf` can be customized for the deployment environment.

Here's how the application can be run:

```
$ pwd
<project root>/build/proc/target/universal
$ bin/procpackage
```

Before the application can be run, the kafka topics need to be created.

In order for the application to run, data need to be loaded in the input topic (`server-log-proc` in the above example). This can be done either through a bash script or through a data loading application in case of a Mesos DC/OS clustered application. We discuss the latter later in the document.


### Deploy on FDP cluster

We will use [fdp-laboratory-base](https://github.com/typesafehub/fdp-laboratory.git) to deploy the application as a Marathon job on the FDP cluster. The application build uses a plugin `sbt-deploy-ssh` which does the plumbing of remote deploy. See `build.sbt` for details. Here are the steps to follow for a remote deploy of the service:

```
$ sbt
> procPackage/deploySsh fdp-kstream-proc
```
This will do the deploy of the `tgz` in `/var/www/html/` of the remote server. The specifications of the remote server are given in a file `deploy.conf` which should be in the project root folder. Here's how `deploy.conf` looks like:

```
servers = [
 {
  name = "fdp-kstream-dsl"
  user = "publisher"
  host = "52.173.78.24"
  port = 9022
  sshKeyFile = "dg-test-fdp.pem"
 },
 {
  name = "fdp-kstream-proc"
  user = "publisher"
  host = "52.173.78.24"
  port = 9022
  sshKeyFile = "dg-test-fdp.pem"
 }
]
```

This file contains the parameters for both the `dsl` and the `proc` packages.

Once the `deploySsh` is complete we can run the application as a Marathon job. The project root folder contains a json file `fdp-kstream-proc.json` that contains all the relevant settings for deployment. Here's the file contents:

```json
{
  "id": "/kstream-app-proc",
  "cmd": "mkdir -p /tmp/data && mv clarknet_access_log_Sep4 /tmp/data && chmod 777 /tmp/data/clarknet_access_log_Sep4 && export PATH=$(ls -d $MESOS_SANDBOX/jre*/bin):$PATH && ./procpackage-0.1/bin/procpackage",
  "env": {
    "INTERFACE_PROC": "0.0.0.0",
    "KAFKA_BROKERS": "broker-0.confluent-kafka.mesos:9301",
    "DIRECTORY_TO_WATCH": "/tmp/data",
    "KAFKA_FROM_TOPIC_PROC": "server-log-proc",
    "KAFKA_ERROR_TOPIC_PROC": "logerr-proc"
  },
  "cpus": 1.0,
  "mem": 8192,
  "disk": 20480,
  "instances": 1,
  "fetch": [
    { "uri": "https://downloads.mesosphere.com/java/jre-8u112-linux-x64.tar.gz" },
    { "uri": "http://jim-lab.marathon.mesos/procpackage-0.1.tgz" },
    { "uri": "ftp://ita.ee.lbl.gov/traces/clarknet_access_log_Sep4.gz" }
  ]
}
```

*Note the file sets the environment variables `$INTERFACE_PROC` that sets the host  of deployment for the http endpoint of the REST microservice. This variable is picked up by the configuration parameter `dcos.http.interface`. For the port number of the endpoint a random free port will be allocated. The value of this port number can be found from the application log file.*

## Running the application from source

### Setup Confluent infrastructure

If you're a developer and wish to run the applications from source you must first have a local running ZooKeeper, Confluent Kafka broker, and Schema Registry.  Confluent maintains docker images for all their solutions on their [cp-docker-images](https://github.com/confluentinc/cp-docker-images/) repository.  This repository contains an examples directory with a `docker-compose.yml` file for running the Confluent platform.  Clone the repository and run the [`cp-all-in-one`](https://github.com/confluentinc/cp-docker-images/blob/3.2.x/examples/cp-all-in-one/docker-compose.yml) example compose file to bring up the necessary infrastructure (ZooKeeper, Kafka Broker, Schema Registry).

### Download & config app to use ClarkNet weblog input data

Download the [Clarknet dataset](http://ita.ee.lbl.gov/html/contrib/ClarkNet-HTTP.html) to a directory of your choice.  Extract the dataset.  Optionally, you may prefer to extract a subset of the dataset to use for testing.

```bash
head -n 100 clarknet_access_log_Aug28 > clarknet_access_log_Aug28_first_100
```

Update `dcos.kafka.loader.directorytowatch` in both the `application_dsl.conf` and `application_proc.conf` under the `./src/main/resources` directory to the directory where you extracted your weblog data.

### Run the app

The `build.sbt` contains two sub modules that can be used to easily run the applications from the CLI:

* WeblogProcessing - `sbt dsl`
* WeblogDriver - `sbl proc`

Each application will use its appropriate `application.conf` and `logback.xml`.  Logs will be generated under `./run/dsl/logs/` and `./run/proc/logs/` respectively.

To trigger the ingestion operation for either app you can add new files in `directorytowatch` or use the `touch` command on any files that already exist in that directory.


## Interfacing with Confluent Connect

The dsl module of the application generates Avro data corresponding to the ingested records to a topic named `avro-topic`. We can set up Confluent Connect to consume from `avro-topic` and write to HDFS. Confluent repository offers out of the box HDFS Sink connectors that can do this. In this section we will discuss how to set this up in our DC/OS clustered environment.

### Prerequisites for the interface

In order for the interface to work, we need to ensure the following:

1. HDFS is installed in the DC/OS cluster and is up 'n running.
2. We have the custom lightbend universe running as a Marathon service.
3. Lightbend universe is set up as the default Universe (index = 0) in the DC/OS packaging.
4. Modifications for running Confluent Connect with HDFS are available the installed Lightbend universe (check the `confluent-connect`package in the Universe).
5. Of course Confluent Connect has to run as a mandatory prerequisite.
6. Confluent schema registry service is up 'n running in the DC/OS cluster.

### Installing the Confluent Connect Distributed Worker

This has to be installed from the Lightbend Universe, which should be the default universe once we ensure Step 3 in the last section. Before installing the package, a bunch of internal topics need to be created manually. These topics are used by the Connect worker.

```bash
$ dcos confluent-kafka topic create dcos-connect-configs --replication 3 --partitions 1

$ dcos confluent-kafka topic create dcos-connect-offsets --replication 3 --partitions 30

$ dcos confluent-kafka topic create dcos-connect-status --replication 3 --partitions 10
```

Check that the topics have been created without error.

```bash
$ dcos confluent-kafka topic list
```

The next step is to install the worker from the Universe. Here are some of the steps to install the worker from the Universe:

1. Select package `confluent-connect v1.0.0-3.2.2` from the Universe
2. Select Advanced Installation since we need to supply the hdfs URL
3. The installation page has a link named *hdfs* in the left pane. Click on the link and fill out *config-url* with the value `http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/endpoints`
4. Complete the installation

The `connect` package should now be available as a Marathon job in the DC/OS UI.

### Installing the HDFS Sink Connector

Once the worker and the internal topics are created, the next step is to create the actual connector. We will be installing the `HdfsSinkConnector` from Confluent which comes as an out of the box connector.

> `HdfsSinkConnector` needs to be configured with a topic from where it will consume Avro data. Please ensure this topic `avro-topic` is pre-installed before we configure the connector. The best way to ensure this is to install the Kafka Streams sample application beforehand which automatically installs all necessary topics.

The only way to install the connector is by using the REST APIs which Connect offers. Here's an example to install our `HdfsSinkConnector`:

```bash
$ curl -X POST -H "Content-Type: application/json" --data '{"name": "ks-hdfs-sink", "config": {"connector.class":"HdfsSinkConnector", "tasks.max":"1", "hdfs.url":"hdfs://hdfs", "topics":"avro-topic", "flush.size":"1000" }}' http://10.8.0.19:9622/connectors
```
Here the last URL `http://10.8.0.19:9622/connectors` refers to the host / port where the `connect` runs.

For more details on how to configure and manage connectors, have a look at this [Confluent Page](http://docs.confluent.io/current/connect/managing.html).

If the above setup steps went fine, then when you run the application for Kafka Streams DSL module, records will be generated in the `avro-topic` and will be consumed by the connector and written in HDFS.
