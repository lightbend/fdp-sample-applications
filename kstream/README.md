# Kafka Streams Sample App for Weblog Processing

This project consists of 2 applications that demonstrate the use of the following features of Kafka Streams:

1. Building and configuring a Streams based topology using DSL as well as the processor based APIs
2. Transformation semantics applied to streams data
3. Stateful transformation using local state stores
4. Interactive queries in Kafka streams applied to a distributed application
5. Implementing custom state stores
6. Interactive queries over custom state stores in a distributed setting

## Application Modules

The project has 2 separate sbt modules - 

* `dslPackage` that implements a streams application based on Kafka Streams DSL
* `procPackage` that implements a streams application based on Kafka Streams Processor APIs

Each of the modules has a separate `Main` class that drive each of the applications. The details of the packaging is in the `build.sbt` file.

## Data used for weblogs

This application processes [archived weblogs](http://ita.ee.lbl.gov/html/contrib/ClarkNet-HTTP.html) in Kafka Streams. The dataset has been downloaded and made available in S3 buckets for processing by the application. The data loading component will download data from the S3 buckets and populate the source Kafka topic.

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

The driver program is the object `WeblogProcessing` that is a plain Scala application with a `main` function. This program assumes that data will be available for streaming in the Kafka topic specified by `dcos.kafka.fromtopic`. The full config of the application consists of the following key/value pairs:

```
dcos {

  kafka {
    brokers = "localhost:9092"
    brokers = ${?KAFKA_BROKERS}

    group = "group-dsl"
    group = ${?KAFKA_GROUP_DSL}

    fromtopic = "server-log-dsl"
    fromtopic = ${?KAFKA_FROM_TOPIC_DSL}

    totopic = "processed-log"
    totopic = ${?KAFKA_TO_TOPIC_DSL}

    summaryaccesstopic = "summary-access-log"
    summaryaccesstopic = ${?KAFKA_SUMMARY_ACCESS_TOPIC_DSL}

    windowedsummaryaccesstopic = "windowed-summary-access-log"
    windowedsummaryaccesstopic = ${?KAFKA_WINDOWED_SUMMARY_ACCESS_TOPIC_DSL}

    summarypayloadtopic = "summary-payload-log"
    summarypayloadtopic = ${?KAFKA_SUMMARY_PAYLOAD_TOPIC_DSL}

    windowedsummarypayloadtopic = "windowed-summary-payload-log"
    windowedsummarypayloadtopic = ${?KAFKA_WINDOWED_SUMMARY_PAYLOAD_TOPIC_DSL}

    errortopic = "logerr-dsl"
    errortopic = ${?KAFKA_ERROR_TOPIC_DSL}

    zookeeper = "localhost:2181"
    zookeeper = ${?ZOOKEEPER_URL}
    
    statestoredir = "/tmp/kafka-streams"
    statestoredir = ${?STATESTOREDIR}
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


### How to package and run the driver application

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

This creates the distribution under the folder `dslpackage-0.1`. The folder has 2 subfolders:

* `bin` containing the startup scripts
* `conf` containing the configuration file `application.conf` and the logging configuration `logback.xml`
* `lib` containing all the jars

The configuration fies in `conf` can be customized for the deployment environment.

Here's how the application can be run:

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

### How to query summary information

As we discussed in the last section, application state is built from the summary information. This is done through continuous computation of the current state into Kafka `KTable`s through `KStream`s.

The application has a microservice built using `akka-http` that publishes http end points for such queries. 

*The configuration of the http end points are driven by the `application.conf` entries `dcos.http.port` and `dcps.http.interface`. For local mode of running, the values can be something like `7070` and `localhost` respectively. However for running in the FDP DC/OS cluster, the value of `dcos.http.interface` is extracted from the environment variable `$INTERFACE_DSL` and the port number assigned is a random free port. The value of this port can be checked from the application log file.*

```
http://localhost:7070/weblog/access/<host-key>
```

This will return the total number of times the host specified by `<host-key>` has been accessed in the log. To get the same as above but in windows of size 1 minute starting from beginning till the current time:

```
http://localhost:7070/weblog/access/win/<host-key>
```


```
http://localhost:7070/weblog/bytes/<host-key>
```

This will return the total payload (number of bytes) transferred for the host specified by `<host-key>` as computed from the log. To get the same as above but in windows of size 1 minute starting from beginning till the current time:

```
http://localhost:7070/weblog/bytes/win/<host-key>
```

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
  "cmd": "export PATH=$(ls -d $MESOS_SANDBOX/jre*/bin):$PATH && ./dslpackage-0.1/bin/dslpackage",
  "env": {
    "INTERFACE_DSL": "0.0.0.0",
    "KAFKA_BROKERS": "broker.confluent-kafka.l4lb.thisdcos.directory:9092"
  },
  "cpus": 1.0,
  "mem": 8192,
  "disk": 20480,
  "instances": 1,
  "fetch": [
    { "uri": "https://downloads.mesosphere.com/java/jre-8u112-linux-x64.tar.gz" },
    { "uri": "http://jim-lab.marathon.mesos/dslpackage-0.1.tgz" }
  ]
}
```

*Note the file sets the environment variables `$INTERFACE_DSL` that sets the host  of deployment for the http endpoint of the REST microservice. This variable is picked up by the configuration parameter `dcos.http.interface`. For the port number of the endpoint a random free port will be allocated. The value of this port number can be found from the application log file.*

## Kafka Streams' Procedure API based module

This application module consists of the following components:

1. The application assumes that source data will be injected into the source topic by an external data loading program. 
2. A driver application that streams data from the Kafka topic loaded in (1), does stateful transformations on the stream data, keeps application state updated in a *custom* state store.
3. The main difference with the DSL based version is that this module uses the Processor APIs of Kafka Streams to build the topology. Also the state store is a custom one - a Bloom Filter based storage that checks for membership of the passed in key.
4. A microservice with http endpoints that allows users to query from Kafka Streams state stores.

### The Driver Program of the application

The driver program is the object `WeblogDriver` that is a plain Scala application with a `main` function. This program assumes that data will be available for streaming in the Kafka topic specified by `dcos.kafka.fromtopic`. The full config of the application consists of the following key/value pairs:

```
dcos {

  kafka {
    brokers = "localhost:9092"
    brokers = ${?KAFKA_BROKERS}

    group = "group-proc"
    group = ${?KAFKA_GROUP_PROC}

    fromtopic = "server-log-proc"
    fromtopic = ${?KAFKA_FROM_TOPIC_PROC}

    zookeeper = "localhost:2181"
    zookeeper = ${?ZOOKEEPER_URL}
    
    statestoredir = "/tmp/kafka-streams"
    statestoredir = ${?STATESTOREDIR}
  }
  
  # http endpoints of the weblog microservice
  http {
    # The port the dashboard listens on
    port = 7071
    port = ${?PORT}

    # The interface the dashboard listens on
    interface = "localhost"
    interface = ${?INTERFACE}
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

This creates the distribution under the folder `procpackage-0.1`. The folder has 2 subfolders:

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

### How to query summary information

The rpocedure is exactly similar to what we discussed in the section for DSL based application. The only difference is that the environment variables for host and port endpoints of http are `$INTERFACE_PROC` and `$PORT_PROC`. 

```
http://localhost:7070/weblog/access/check/<host-key>
```

This will return `true` if the `<host-key>` is present in the store, `false`, otherwise.


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
  "cmd": "export PATH=$(ls -d $MESOS_SANDBOX/jre*/bin):$PATH && ./dslpackage-0.1/bin/procpackage",
  "env": {
    "INTERFACE_PROC": "0.0.0.0",
    "KAFKA_BROKERS": "broker.confluent-kafka.l4lb.thisdcos.directory:9092"
  },
  "cpus": 1.0,
  "mem": 8192,
  "disk": 20480,
  "instances": 1,
  "fetch": [
    { "uri": "https://downloads.mesosphere.com/java/jre-8u112-linux-x64.tar.gz" },
    { "uri": "http://jim-lab.marathon.mesos/procpackage-0.1.tgz" }
  ]
}
```

*Note the file sets the environment variables `$INTERFACE_PROC` that sets the host  of deployment for the http endpoint of the REST microservice. This variable is picked up by the configuration parameter `dcos.http.interface`. For the port number of the endpoint a random free port will be allocated. The value of this port number can be found from the application log file.*
