# Model serving Akka Streams and Kafka Streams

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
lightweight embedded database and, more concretely, to directly query the latest state of your stream processing application, without needing to materialize that state to external databases or external storage first.


![Queriable state](images/queryablestate.png)

Both Akka Streams and Kafka streams implementation support queryable state

# Scaling

Both Akka and Kafka Streams implementations are in JVM implementations.
Given that the source of streams is Kafka, they both can be deployed as a cluster.
The Figure below shows Kafka Streams cluster. Akka Streams implementation can be scaled the same way

![scaling](images/Kafkastreamsclusters.png)


# Prerequisites

This application relies on Kafka (current version is 1.0) and requires a Kafka deployment available.
It uses 2 topics:
* `models_data` - topic used for sending data
* `models_models` - topic used for sending models
Model provider and data provider applications check if their corresponding topics exist. Run them 
first if not sure whether the topics exist.

It also relies on InfluxDB/Grafana for visualization. Both need to be installed before running applications

For InfluxDB, the database `serving` with retentionPolicy `default` is used. 
The application checks on startup whether the database exists and create it, if necessary.
The application also ensures that the Grafana data source and dashboard definitions exist and create them, if necessary.


# Build the code

We recommend using [IntelliJ IDEA](https://www.jetbrains.com/idea/) for managing and building the code. 
The project is organized as several modules:

* `akkastreamssvc` - Akka Streams implementation of model serving
* `client` - Data and model loader used to run either Akka or Kafka streams application
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
    # For IntelliJ users, just import a project and use IntelliJ commands



# Package, Configure, Deploy, and Run

This project contains 3 executables:
* `akkaserver`    - Akka Streams implementation of model serving
* `kafkaserver`   - Kafka Streams implementation of model serving
* `dataprovider`  - Data publisher

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
After a successful build, we can see the images in our local _docker registry_:
```
$docker images

REPOSITORY                                          TAG    IMAGE ID      CREATED         SIZE
fdp-reg.lightbend.com:443/model-server-akkastreams  1.1.0  aab5a948b8b8  10 minutes ago  727MB
fdp-reg.lightbend.com:443/model-server-kstreams     1.1.0  2d4f047f8ddb  10 minutes ago  708MB
fdp-reg.lightbend.com:443/model-server-publisher    1.1.0  a3040c809984  10 minutes ago  606MB
...

```

### Publishing to an external Docker repository

To publish the resulting _Docker_ images to an external public repository, we need to:
- configure the address of the repository in the `build.sbt`

```
sbt docker:publishLocal

```
## Component Configuration

This application uses a file-based configuration with _environment variables_ overrides.
A common practice is to use well known endpoint names for the target environment in the configuration file
and allow for overrides from environment variables.

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

### `Publisher`

The `publisher` component support the following configuration:

#### Mandatory Parameters
- `KAFKA_BROKERS_LIST`: a comma-separated list of the kafka brokers to contact in the form "<host1>:<port1>,<host2>:<port2>,..." 
- `ZOOKEEPER_URL`: the URL to the zookeeper service

#### Optional Parameters
- `DATA_PUBLISH_INTERVAL` (default: `1 second`): The time delay between publishing data records. 
- `MODEL_PUBLISH_INTERVAL` (default: `5 minutes`): The time delay between publishing models.
- `DATA_DIRECTORY` (default: `./data`): The directory where to search for data files  
- `DATA_FILENAME` (default: `winequality_red.csv`): The data file in the `DATA_DIRECTORY` to use as source for the published records.   

### `akka`| `kafka` -`svc`

The _model serving_ service, in both its _kafka streams_ and _akka streams_ implementations, 
requires the following configuration parameters:

 - `KAFKA_BROKERS_LIST`: a comma-separated list of the kafka brokers to contact in the form "<host1>:<port1>,<host2>:<port2>,..."
 - `ZOOKEEPER_URL`: the URL to the zookeeper service
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
 
In any case, the required external services should be reachable from the host executing the application of any of its components. 
Note that when running against services installed on DC/OS, their DNS names are not resolvable and we should use IP addresses instead.  

### Running the Publisher

#### On Docker

Build and publish a local image as described in []

docker run -e KAFKA_BROKERS_LIST=10.0.7.196:1025 -e ZOOKEEPER_URL=10.0.5.33/dcos-service-kafka fdp-reg.lightbend.com:443/model-server-publisher:1.1.0


`dataprovider` application allow for changing of frequency of sending data, by
specifying desired frequency (in ms) as an application parameter. If the parameter is not specified
data is send once a sec and model - once every 5 mins.

Both `akkaserver` and `kafkaserver` implement queryable state. 

To query `akkaserver` state connect your browser to `host:5500/stats` to get statistics of the current execution
Currently `akkaserver` supports only statistics for a given server. If a cluster is used, each server needs to be
queried (with the same port)


To query `kafkaserver` state connect your browser to `host:8888`. This contains several URLs:
* `/state/instances` returns the list of instances participating in the cluster
* `/state/instances/{storeName}` returns the list of instances containing a store. Store name used 
in our application is `modelStore`
* `/state/{storeName}/value` returns current state of the model serving

## Running on the server
The applications are included in `fdp-package-sample-apps` docker image. See documentation there
for running them on the server




