## Kafka Streams Sample App for Weblog Processing

This application processes [archived weblogs](  http://ita.ee.lbl.gov/html/contrib/ClarkNet-HTTP.html) in Kafka Streams. The application consists of the following components:

1. A data loader that loads a Kafka topic from the archived data
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

    group = "group"
    group = ${?KAFKA_GROUP}

    fromtopic = "server-log"
    fromtopic = ${?KAFKA_FROM_TOPIC}

    totopic = "processed-log"
    totopic = ${?KAFKA_TO_TOPIC}

    summaryaccesstopic = "summary-access-log"
    summaryaccesstopic = ${?KAFKA_SUMMARY_ACCESS_TOPIC}

    summarypayloadtopic = "summary-payload-log"
    summarypayloadtopic = ${?KAFKA_SUMMARY_PAYLOAD_TOPIC}

    errortopic = "logerr"
    errortopic = ${?KAFKA_ERROR_TOPIC}

    zookeeper = "localhost:2181"
    zookeeper = ${?ZOOKEEPER_URL}
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
4. Sets up a REST microservice that offers query for the state store `WeblogMicroservice`.


### How to run the driver application

The driver application is built from `sbt` as a fat assembly jar which excludes the config file `application.conf` and the logback xml `logback.xml` file.

```
$ sbt clean assembly
```

The jar is a Java application that can be run from the command line as follows:

```
java -Dconfig.file=<path-to-application.conf> \
     -Dlogback.configurationFile=<path-to-logback.xml> \
     -cp ./fdp-kstream-assembly-0.1.jar \
     com.lightbend.fdp.sample.kstream.WeblogProcessing 7070 localhost
```

In case of running the application in a distributed mode, another instance can be run exactly as above only with a different port number (and may be on a different host):

```
java -Dconfig.file=<path-to-application.conf> \
     -Dlogback.configurationFile=<path-to-logback.xml> \
     -cp ./fdp-kstream-assembly-0.1.jar \
     com.lightbend.fdp.sample.kstream.WeblogProcessing 7071 localhost
```

Before the application can be run, the kafka topics need to be created. Here are some sample commands for doing the same:

```
$ $KAFKA_HOME/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic logerr
$ $KAFKA_HOME/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic server-log
$ $KAFKA_HOME/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic processed-log
$ $KAFKA_HOME/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic summary-access-log
$ $KAFKA_HOME/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic summary-payload-log
```

In order for the application to run, data need to be loaded in the input topic (`server-log` in the above example). This can be done either through a bash script or through a data loading application in case of a Mesos DC/OS clustered application. We discuss the latter later in the document.

### How to query summary information

As we discussed in the last section, application state is built from the summary information. This is done through continuous computation of the current state into Kafka `KTable`s through `KStream`s.

The application has a microservice built using `akka-http` that publishes http end points for such queries.

```
http://localhost:7070/weblog/access/<host-key>
```

This will return the total number of times the host specified by `<host-key>` has been accessed in the log.


```
http://localhost:7070/weblog/bytes/<host-key>
```

This will return the total payload (number of bytes) transferred for the host specified by `<host-key>` as computed from the log.