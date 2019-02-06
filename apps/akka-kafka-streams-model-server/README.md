# Model Serving with Kafka, Akka Streams, and Kafka Streams

This sample application demonstrates one way to update and serve machine learning models in a streaming context, using either Akka Streams or Kafka Streams. For a more recent treatment of this subject, see this [Lightbend open-source tutorial on model serving](https://github.com/lightbend/model-serving-tutorial).

> **DISCLAIMER:** This sample application is provided as-is, without warranty. It is intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but it has not gone through a robust validation process, nor does it use all the techniques commonly employed for highly-resilient, production applications. Please use it with appropriate caution.

## Overall Architecture

Here is a high-level view of the overall model serving architecture:

![Overall architecture of model serving](images/overallModelServing.png)

It is similar to the [dynamically controlled stream pattern](https://data-artisans.com/blog/bettercloud-dynamic-alerting-apache-flink) documented by [data Artisans](https://data-artisans.com/) for [Apache Flink](https://flink.apache.org).

This architecture assumes two data streams - one containing data that needs to be scored, and one containing the model updates. The streaming engine contains the current model used for the actual scoring in memory. The results of scoring can be either delivered to the customer or used by the streaming engine internally as a new stream - input for additional calculations. If there is no model currently defined, the input data is dropped. When the new model is received, it is instantiated in memory, and when instantiation is complete, scoring is switched to a new model. The model stream can either contain the binary blob of the data itself or the reference to the model data stored externally (pass by reference) in a database or a filesystem, like HDFS or S3.

This approach effectively uses model scoring as a new type of functional transformation, that can be used along with any other streaming functional transformations.

Although the overall architecture above shows a single model, a streaming application could score multiple models simultaneously, such as for blue-green testing, ensembles, etc.

## Akka Streams

The Akka implementation is based on the usage of a custom _stage_, which is a fully type-safe way to encapsulate functionality into a reusable functional transformation.

The stage implements the stream processor functionality from the architecture diagram.
With this component in place, the overall implementation looks as follows:

![Akka streams model serving](images/Akkajoin.png)

## Kafka Streams

The Kafka Streams implementation leverages a custom store containing the current execution state.
With this store in place, the implementation of model serving using Kafka
Streams becomes very simple; it’s basically two independent streams coordinated via a shared store.

![Kafka streams model serving](images/kafkastreamsJoin.png)

## Queryable state

Kafka Streams [recently introduced](https://docs.confluent.io/current/streams/developer-guide.html#id8) _queryable state_, which is a nice approach for providing convenient access to stream state information, e.g., for execution monitoring, dashboards, etc.

This feature allows treating the stream processing layer as a lightweight embedded database and, more concretely, to directly query the latest state of your stream processing application, without needing to materialize that state to external external storage first.

![Queriable state](images/queryablestate.png)

Both `akkastreamssvc` and `kafkastreamssvc` implement queryable state, where queryable state is implemented for the Akka example using Akka HTTP.

To query the `akkaserver` state, connect your browser to `host:5500/stats` to get statistics of the current execution.
Currently `akkaserver` supports only statistics for a given server. If a cluster is used, each server needs to be queried separately with the same port.

To query the `kafkaserver` state, connect your browser to `host:8888`. This supports several URLs:

* `/state/instances` returns the list of instances participating in the cluster
* `/state/instances/{storeName}` returns the list of instances containing a store. Store name used
in our application is `modelStore`
* `/state/{storeName}/value` returns current state of the model serving

## Scaling

Both Akka and Kafka Streams implementations are JVM implementations.
Given that the data source of the streams is a pair of Kafka topics, both apps can be deployed as a cluster. The following Figure shows a Kafka Streams cluster. An Akka Streams implementation can be scaled the same way:

![scaling](images/Kafkastreamsclusters.png)

## Prerequisites

Kafka is required and must already be installed (the current version is 1.0).
The app uses two topics:

* `models_data` - for data to be scored
* `models_models` - for new models

The model provider and data provider applications check if their corresponding topics exist.
Run them first if you are not sure whether or not the topics already exist.

This app also relies on InfluxDB and Grafana for visualization. Both need to be installed before running the applications.

For InfluxDB, the database `serving` with retentionPolicy `default` is used.
The application checks on startup whether or not the database exists and creates it, if necessary.
The application also ensures that the Grafana data source and dashboard definitions exist, creating them if necessary.

## Building the Code

While you can run our prebuilt Docker images, as discussed below, you may wish to modify the code and build your own images.

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

Building the app can be done using the convenient `build.sh` or `sbt`.

For `build.sh`, use one of the following commands:

```bash
build.sh
build.sh --push-docker-images
```

Both effectively run `sbt clean compile docker`, while the second variant also pushes the images to your Docker Hub account. _Only use this option_ if you first change `organization in ThisBuild := CommonSettings.organization` to `organization in ThisBuild := "myorg"` in `source/core/build.sbt`!

To use `sbt`:

```bash
cd source/core
sbt clean compile docker
```

You can use the `sbt` target `dockerPush` to push the images to Docker Hub, but only after changing the `organization` as just described. You can publish to your local (machine) repo with the `docker:publishLocal` target.

For IDE users, just import a project and use IDE commands, but it is necessary to run `sbt clean compile` at least once to compile the `protobufs` subproject correctly.

## Package, Configure, Deploy, and Run

This project contains 3 executable modules:

* `akkastreamssvc`  - Akka Streams implementation of model serving
* `kafkastreamssvc` - Kafka Streams implementation of model serving
* `publisher`       - Data publisher

Each application can run either locally on your machine or in the cluster.

### Packaging with Docker

We just described how to build the Docker images. Let's discuss in more detail.

For this section, we assume you have a working Docker installation on your machine.

We use Docker to containerize the different runtime components of this application.

The creation of Docker images is done by the
[sbt-native-packager](https://www.scala-sbt.org/sbt-native-packager/formats/docker.html)
plugin, using the `docker` packaging format plugin.

For local testing, the Docker images can be created and added to the local _docker repository_

Use the command:

```bash
sbt docker:publishLocal
```

> **Note:** Running this command requires you to be logged into Docker Hub to be able to get the base image.

After a successful build, you will have the following images in your local Docker registry (output reformatted...):

```bash
$ docker images

REPOSITORY                          TAG    IMAGE ID      CREATED         SIZE
...
lightbend/fdp-akka-kafka-streams-model-server-kafka-streams-server   1.2.0 a6686af97700 9 days ago  1.99GB
lightbend/fdp-akka-kafka-streams-model-server-akka-streams-server    1.2.0 50ec77bdc828 9 days ago  2GB
lightbend/fdp-akka-kafka-streams-model-server-model-publisher        1.2.0 1ece138c81c8 9 days ago  575MB
```

### Publishing to an External Docker Repository

To publish the resulting Docker images to an external public repository, you need to configure
the address of this repository in `build.sbt`. Replace the default value for your own docker registry:

```scala
// change to use your own (private) repo, e.g.:docker-registry.mycompany.com
val dockerRepositoryUrl = "lightbend"
```

Note that additional credentials might be required depending on the Docker registry provider (e.g., _Sonatype_). Please refer to your provider for credentials.

Now either use the convenient `build.sh` or `sbt` to publish:

```bash
build.sh --push-docker-images
```

or

```bash
cd source/core
sbt docker:publish
```

### Component Configuration

This application uses [Lightbend config](https://github.com/lightbend/config) configuration with _environment variables_ overrides.
A common practice is to use well known endpoint names for the target environment in the configuration file and allow for overrides from _environment variables_ to run in different environments or in local mode.

This is an example of the configuration for `influxDB`, which indicates the default value in the cluster as well as the environment variable override:

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

You can run the complete application locally on your machine from source, from the Docker images, or with a combination of the two, depending of your goals. This is of course most convenient while testing.

For this to work, you'll also need to run Kafka locally or be able to reach Kafka brokers in a cluster where network access is enabled, e.g., a small test Kafka cluster.

For example, to test the end-to-end execution, run all processes in Docker containers. If you are in the middle of a _develop-run-test_ cycle of the _model serving_ part, for example, you could run the `producer` Docker image, while executing the server code from `sbt` or your IDE of choice.

In any case, the required external services should be reachable from the host executing any of the components of the application.

Note that when running against services installed in a cluster with _virtual IP addressing_, the names may not be resolvable and IP addresses may be required instead.

### Publisher: Running Locally

To run the _Publisher_ component, we need first to have the IP address for the Kafka broker, either an external cluster or installed and executed locally on your machine.

See this guide for a quick-start local installation: [Kafka Quick Start](https://kafka.apache.org/quickstart).

#### Running from `sbt` (or an IDE)

The easiest way to provide the necessary configuration when running locally is by editing the `application.conf` file that corresponds to the _publisher_ component. It is located at: `publisher/src/main/resources/application.conf`.

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

```bash
sbt publisher/run
```

Or just run the `com.lightbend.kafka.DataProvider` class in your IDE.

### Running Using Docker

- Build and publish the local Docker image as described in (Packaging)[#Packaging]
- Start the docker container for the _Model and Data publisher_ with `docker run`

Note the configuration provided as _environment variables_ through the Docker `-e` option.

In the following example, we pass the mandatory `KAFKA_BROKERS_LIST` and `ZOOKEEPER_URL` parameters.
See the [Publisher configuration](#publisher-configuration) for all the options.

```bash
docker run -e KAFKA_BROKERS_LIST=<kafka-broker> -e ZOOKEEPER_URL=<zk-url> \
  lightbend/fdp-akka-kafka-streams-model-server-model-publisher:2.0.0-OpenShift
```
Change the `2.0.0-OpenShift` Fast Data Platform version if necessary.

### Running Model Serving

We require the configuration as specified in [Model Serving Configuration](#model-serving-configuration)

#### Running from `sbt` (or an IDE)

The easiest way to provide the necessary configuration when running locally is by editing the `application.conf` file that corresponds to the _akka_ or _kafka_ streams component. It is located at: `<akka|kafka>streamsvc/src/main/resources/application.conf`.

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

```bash
# For the akka streams implementation
sbt akkastreamssvc/run

# For kafka streams implementation
sbt kafkastreamssvc/run
```

Or run it directly from your IDE using the classes `com.lightbend.modelServer.modelServer.AkkaModelServer` for Akka Streams and `com.lightbend.modelserver.withstore.ModelServerWithStore` for Kafka Streams.

#### Running with Docker

The docker images for the _Model Serving_ implementation contain the configuration provided in the `application.conf` file at the moment the image was built.

To override the configuration **at runtime**, we can use the _environment variable_ replacement as we did for the _publisher_ component.
In the section [Model Serving Configuration](#model-serving-configuration) we can see all supported configuration parameters.

When running locally, remember to ensure that you can resolve the target services or use their IP addresses instead of DNS names.

Example:

```bash
docker run -e KAFKA_BROKERS_LIST=10.0.7.196:1025 \
  -e ZOOKEEPER_URL=<zk-url> \
  -e GRAFANA_HOST=<grafana-host> \
  -e GRAFANA_PORT=<grafana-port \
  -e INFLUXDB_HOST=<influxdb-host> \
  -e INFLUXDB_PORT=<influxdb-port> \
  lightbend/fdp-akka-kafka-streams-model-server-akka-streams-server:2.0.0-OpenShift
```

Change the version `2.0.0-OpenShift` as required.

## Deploying to OpenShift or Kubernetes

For OpenShift or Kubernetes, the project provides `modelserverchart` chart. (See [this](https://docs.bitnami.com/kubernetes/how-to/create-your-first-helm-chart/#values) for an introduction to Helm, if it's new to you.)

This chart has `chart.yaml` and `values.yaml` defining the content and values used in the chart.
It also has 2 deployment yaml files:

1. `publisherinstall.yaml` installs publisher pod. This pod runs model data publisher. It also contains `init-pod` (implemented using `busybox` image) and responsible for loading data files and mounting them for access by the publisher.
2. `modelserviceinstall.yaml` installs either akka or kafka server (depending on the values configuration).

The chart assumes that _Ingress_ is created for exposing the model serving UI.

### Running a Custom Image on Kubernetes or OpenShift

Edit the `helm/values.yaml.template` and change the image locations or simply override the values when invoking `helm install ...`

