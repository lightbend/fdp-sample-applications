# Model serving Akka Streams and Kafka Streams

A sample application that demonstrates one way to update and serve machine learning models in a streaming context, using either Akka Streams or Kafka Streams.

> **Disclaimer:** This sample application is provided as-is, without warranty. It is intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but it has not gone through a robust validation process, nor does it use all the techniques commonly employed for highly-resilient, production applications. Please use it with appropriate caution.

# Overall Architecture

A high-level view of the overall model serving architecture (
similar to [dynamically controlled stream](https://data-artisans.com/blog/bettercloud-dynamic-alerting-apache-flink)) 

![Overall architecture of model serving](images/overallModelServing.png)


This architecture assumes two data streams - one containing data that needs to be scored, and one containing the model updates. The streaming engine contains the current model used for the actual scoring in memory. The results of scoring can be either delivered to the customer or used by the streaming engine internally as a new stream - input for additional calculations. If there is no model currently defined, the input data is dropped. When the new model is received, it is instantiated in memory, and when instantiation is complete, scoring is switched to a new model. The model stream can either contain the binary blob of the data itself or the reference to the model data stored externally (pass by reference) in a database or a filesystem, like HDFS or S3.
Such approach effectively using model scoring as a new type of functional transformation, that can be used by any other stream functional transformations.
Although the overall architecture above is showing a single model, a single streaming engine could score multiple models simultaneously.

# Akka Streams

Akka implementation is based on the usage of a custom stage, which is a fully type-safe way to encapsulate required functionality.
The stage implements stream processor functionality from the overall architecture diagram.
With such component in place, the overall implementation is going to look as follows:


![Akka streams model serving](images/Akkajoin.png)


# Kafka Streams

Kafka Streams implementation leverages custom store containing current execution state.
With this store in place, the implementation of the model serving using Kafka 
Streams becomes very simple, it’s basically two independent streams coordinated via a shared store. 

![Kafka streams model serving](images/kafkastreamsJoin.png)


# Queryable state

Kafka Streams  recently [introduced](https://docs.confluent.io/current/streams/developer-guide.html#id8) queryable state, which is
a nice approach to execution monitoring.
This feature allows treating the stream processing layer as a 
lightweight embedded database and, more concretely, to directly query the latest state of your stream processing application, 
without needing to materialize that state to external databases or external storage first.


![Queriable state](images/queryablestate.png)

Both `akkastreamssvc` and `kafkastreamssvc` implement queryable state. 

To query `akkaserver` state connect your browser to `host:5500/stats` to get statistics of the current execution
Currently `akkaserver` supports only statistics for a given server. If a cluster is used, each server needs to be
queried (with the same port)

To query `kafkaserver` state connect your browser to `host:8888`. This contains several URLs:
* `/state/instances` returns the list of instances participating in the cluster
* `/state/instances/{storeName}` returns the list of instances containing a store. Store name used 
in our application is `modelStore`
* `/state/{storeName}/value` returns current state of the model serving

# Scaling

Both Akka and Kafka Streams implementations are in JVM implementations.
Given that the source of streams is Kafka, they both can be deployed as a cluster.
The Figure below shows Kafka Streams cluster. Akka Streams implementation can be scaled the same way

![scaling](images/Kafkastreamsclusters.png)


# Prerequisites

Overall implement relies on Kafka (current version is 1.0) and requires kafka to be installed.
It uses 2 queues:
* `models_data` - queue used for sending data
* `models_models` - queue used for sending models
Model provider and data provider applications check if their corresponding queues exist. 
Run them first if not sure whether queues exist

It also relies on InfluxDB/Grafana for visualization. Both need to be installed before running applications

For InfluxDB, the database `serving` with retentionPolicy `default` is used. 
The application checks on startup whether the database exists and create it, if necessary.
The application also ensures that the Grafana data source and dashboard definitions exist and create them, if necessary.

# Building the code
 
The project is organized as several modules:

* `akkastreamssvc` - Akka Streams implementation of model serving
* `publisher` - Data and model loader used to run either Akka or Kafka streams application
* `configuration` - Shared configurations and InfluxDB support (see [prerequisites](#Prerequisites))
* `model` - Implementation of both Tensorflow anf PMML models.
* `protobufs` - Shared models in protobuf format.
* `kafkastreamssvc` -  Kafka Streams implementation of model serving.

Additionally, the following folders are used:

* `data` - some data files for running the applications
* `images` - diagrams used for this document

The build is done via `sbt`

    cd KafkaStreamsModelServer
    sbt clean compile
    
For IntelliJ users, just import a project and use IntelliJ commands
make sure that you run `sbt clean compile` at least once to compile protobufs


# Package, Configure, Deploy, and Run

This project contains 3 executable modules:
* `akkastreamssvc`  - Akka Streams implementation of model serving
* `kafkastreamssvc` - Kafka Streams implementation of model serving
* `publisher`       - Data publisher

Each application can run either locally (on user's machine) or on the server.

## Packaging

For this section we assume you have a working _docker_ installation on your machine |
------------------------------------------------------------------------------------|

We use _docker_ to containerize the different runtime components of this application. 

The creation of _docker_ images is done by the 
[sbt-native-packager](https://www.scala-sbt.org/sbt-native-packager/formats/docker.html) 
plugin, using the `docker` packaging format plugin.

For local testing, the _docker_ images can be created and added to the local _docker repository_ 

Use the command:
```
sbt docker:publishLocal

```
`Note:` Running of this command requires you to be logged into the docker repo to be able to get the base image

After a successful build, we can see the images in our local _docker registry_:
```
$docker images

REPOSITORY                          TAG    IMAGE ID      CREATED         SIZE
lightbend/fdp-akka-kafka-streams-model-server-kafka-streams-server                                                            1.2.0               a6686af97700        9 days ago          1.99GB
lightbend/fdp-akka-kafka-streams-model-server-akka-streams-server                                                             1.2.0               50ec77bdc828        9 days ago          2GB
lightbend/fdp-akka-kafka-streams-model-server-model-publisher                                                                 1.2.0               1ece138c81c8        9 days ago          575MB
````

### Publishing to an external Docker repository

To publish the resulting _Docker_ images to an external public repository, we need to configure 
the address of this repository in the `build.sbt`.
This is done directly in the `build.sbt` file, by replacing the default value for your own docker registry:
```
// change to use your own (private) repo, e.g.:docker-registry.mycompany.com
val dockerRepositoryUrl = "lightbend" 
```
Note that additional credentials might be required depending of the docker registry provider. 
Please refer to your provider for credentials.

```
sbt docker:publish

```
## Component Configuration

This application uses [Lightbend config](https://github.com/lightbend/config) configuration with _environment variables_ overrides.
A common practice is to use well known endpoint names for the target environment in the configuration file
and allow for overrides from _environment variables_ to run in different environments or in local mode.

This is an example of the configuration for `influxDB`, which indicates the default value in the cluster 
as well as the environment variable override:

```
influxdb {
  host = "http://influxdb.marathon.l4lb.thisdcos.directory"
  host = ${?INFLUXDB_HOST}
  port = "8086"
  port = ${?INFLUXDB_PORT}
}
```

There's an `application.conf` file on each executable project, under `src/main/resources/`

### Publisher Configuration

The `publisher` component support the following configuration:

#### Mandatory Parameters
- `KAFKA_BROKERS_LIST`: a comma-separated list of the kafka brokers to contact in the form `"<host1>:<port1>,<host2>:<port2>,..."` 

#### Optional Parameters
- `DATA_PUBLISH_INTERVAL` (default: `1 second`): The time delay between publishing data records. 
- `MODEL_PUBLISH_INTERVAL` (default: `5 minutes`): The time delay between publishing models.
- `DATA_DIRECTORY` (default: `./data`): The directory where to search for data files  
- `DATA_FILENAME` (default: `winequality_red.csv`): The data file in the `DATA_DIRECTORY` to use as source for the published records.   

### Model Serving Configuration

The _model serving_ service, in both its _kafka streams_ and _akka streams_ implementations, 
requires the following configuration parameters:

 - `KAFKA_BROKERS_LIST`: a comma-separated list of the kafka brokers to contact in the form "<host1>:<port1>,<host2>:<port2>,..."
 - `model_server.port` : port used for a queryable state
 - `GRAFANA_HOST`: the host where the _grafana_ service is running (DNS name or IP address)
 - `GRAFANA_PORT`: the port of the grafana service
 - `INFLUXDB_HOST`: the host where the _influxDB_ service is running (DNS name or IP address)
 - `INFLUXDB_PORT`: the port of the _influxDB_ service
 
## Running Locally

We can run the complete application from source, from the _docker_ images or with a combination of the two, 
depending of our goal.
For example, to test the end-to-end execution, we can opt to run all processes in _docker_ containers
 while if we are in the middle of a _develop-run-test-develop_ cycle of the _model serving_ part, 
 we could run the `producer` in its container, while executing the server code from `sbt` or our IDE of choice.
 
In any case, the required external services should be reachable from the host executing the any of the components of the application.
 
Note that when running against services installed on DC/OS, their DNS names are not resolvable and we should use IP addresses instead.  

### Publisher: Running Local

To run the _Publisher_ component, we need first to have the IP address for the Kafka broker.
Those dependencies can run in an external cluster or can be installed and executed locally.
See this guide for a local install: [Kafka Quick Start](https://kafka.apache.org/quickstart) 

#### Running from `sbt` (or an IDE)

The easiest way to provide the necessary configuration when running locally is by editing the `application.conf` file 
that corresponds to the _publisher_ component. It is located at: `publisher/src/main/resources/application.conf`. 

Update the values provided there with the corresponding configuration for your local system:

```
kafka.brokers = "localhost:29092"
publisher {
  data_publish_interval = 1 second
  model_publish_interval = 5 minutes
  data_dir = "data"
  data_file = "winequality_red.csv"
``` 

Then use `sbt` to run the process:

```
sbt publisher/run
```

Or just run it from Intellij `com.lightbend.kafka.DataProvider`

#### Running using Docker

- Build and publish the local _Docker_ image as described in (Packaging)[#Packaging]
- Start the docker container for the _Model and Data publisher_ with `docker run`
   - Note the configuration provided as _environment variables_ through the _Docker_ `-e` option.

In the following example, we pass the mandatory `KAFKA_BROKERS_LIST` and `ZOOKEEPER_URL` parameters. 
See the [Publisher configuration](#publisher-configuration) for all options. 

```
docker run -e KAFKA_BROKERS_LIST=<kafka-broker> \
           lightbend/fdp-akka-kafka-streams-model-server-model-publisher:X.Y.Z
```
Where `X.Y.Z` is FDP version.

### Running Model Serving

We require the configuration as specified in [Model Serving Configuration](#model-serving-configuration)

#### Running from `sbt` (or an IDE)

The easiest way to provide the necessary configuration when running locally is by editing the `application.conf` file 
that corresponds to the _akka_ or _kafka_ streams component. 
It is located at: `<akka|kafka>streamsvc/src/main/resources/application.conf`. 

Update the values provided there with the corresponding configuration for your local system:

```
kafka.brokers = "localhost:29092"
model_server.port = 5500

grafana {
  host = "10.0.4.61"
  port = 20749
}

influxdb {
  host = "10.0.4.61"
  port = 18559
}
``` 

The use `sbt` to run the process:

```
# For the akka streams implementation
sbt akkastreamssvc/run

# For kafka streams implementation
sbt kafkastreamssvc/run
```

Or run it directly from Intellij using classes `com.lightbend.modelServer.modelServer.AkkaModelServer` for Akka
and `com.lightbend.modelserver.withstore.ModelServerWithStore` for Kafka.


#### Running on Docker

The docker images for the _Model Serving_ implementation contain the configuration provided in the `application.conf` file 
at the moment the image was built.

To override the configuration **at runtime**, we can use the _environment variable_ replacement as we did for the _publisher_ component.
In the section [Model Serving Configuration](#model-serving-configuration) we can see all supported configuration parameters.

When running locally, remember to ensure that you can resolve the target services or use their IP addresses instead of DNS names.

Example: 
```
docker run -e KAFKA_BROKERS_LIST=10.0.7.196:1025 \
        -e ZOOKEEPER_URL=<zk-url> \
        -e GRAFANA_HOST=<grafana-host> \
        -e GRAFANA_PORT=<grafana-port \ 
        -e INFLUXDB_HOST=<influxdb-host> \
        -e INFLUXDB_PORT=<influxdb-port> \
        lightbend/fdp-akka-kafka-streams-model-server-akka-streams-server:X.Y.Z
```
Where `X.Y.Z` is FDP version.

## Running a Custom Image on the DC/OS

To run a custom application on the DC/OS:
 - apply the desired modifications
 - [publish the image to your docker repository](#publishing-to-an-external-docker-repository)
 - copy `json.template` files in the corresponding`source/main/resources` directory to the location for that can be accessed by `DCOS` utility, update values as required and
 - execute the `DCOS` command to install.
 
## Deploying to Kubernetes

For Kubernetes the project provides `modelserverchart` chart. See [this](https://docs.bitnami.com/kubernetes/how-to/create-your-first-helm-chart/#values) for Helm intro
This chart has `chart.yaml` and `values.yaml` defining the content and values used in the chart.
It also has 2 deployment yaml files:
1. `publisherinstall.yaml` installs publisher pod. This pod runs model data publisher. It also contains
init-pod (implemented using `busybox` image) and responsible for loading data files and mounting them for access
by the publisher.
2. `modelserviceinstall.yaml` installs either akka or kafka server (depending on the values configuration). 

The chart assumes that Ingress is created for exposing model serving UI
