# FDP Sample Applications

This repo contains the sample applications for [Lightbend Fast Data Platform](https://www.lightbend.com/products/fast-data-platform), version 1.3.0 and later. For information about these applications, see the [Fast Data Platform documentation](https://developer.lightbend.com/docs/fast-data-platform/current/), specifically the [Sample Applications](https://developer.lightbend.com/docs/fast-data-platform/current//user-guide/sample-apps/index.html) chapter.

> **Disclaimer:** These sample applications are provided as-is, without warranty. They are intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but they have not gone through a robust validation process, nor do they use all the techniques commonly employed for highly-resilient, production applications. Please use them with appropriate caution.

There are branches in this repository that correspond to Fast Data Platform releases. For example, `release/1.3.0` for the 1.3.0 release. The `develop` branch is the "bleeding edge", which is usually fine to use, but if you encounter problems, consider using the release branch for your installation.

The applications are organized in folders with each of them containing details of how to use and install them as part of your environment. Here's the list of the applications:

* [KillrWeather](apps/killrweather/README.md): KillrWeather is a reference application adopted from the [original Datastax version](https://github.com/killrweather/killrweather), which shows how to easily leverage and integrate Apache Spark, Apache Cassandra, Apache Kafka, Akka, and InfluxDB for fast, streaming computations. This application focuses on the use case of time series data.

* [Model Server](apps/akka-kafka-streams-model-server/README.md): A sample application that demonstrates one way to update and serve machine learning models in a streaming context, using either Akka Streams or Kafka Streams.

* [Network Intrusion](apps/nwintrusion/README.md): A network intrusion detector application that ingests network traffic data from Kafka and runs an online clustering algorithm using Spark Streaming to detect anomalies.

* [VGG Training on CIFAR-10 data using BigDL](apps/bigdl/README.md): This is a demonstration of using a Spark based deep learning library on Fast Data Platform. We use [Intel BigDL](https://github.com/intel-analytics/BigDL) library and train a VGG Network on CIFAR-10 data set.

* [Taxiride Application using Flink](apps/flink/README.md): This is an adaptation of the publicly available [Flink training example from dataArtisans](http://training.data-artisans.com/). The application uses Flink as the streaming platform to train a regression classifier that predicts taxi travel times on a data set from New York City.

* [Processing Web Logs with Kafka Streams](apps/kstream/README.md): This example uses Kafka Streams APIs to process weblogs. It shows the power of both the higher level DSLs as well as the lower level Processor based APIs.

## Installation

Each application contains detailed instructions on how to build and install the application locally or on a DC/OS cluster or Kubernetes cluster (experimental support).

Here's how to install all of them from a centralized command line script into a DC/OS cluster. The script assumes the following:

* A DC/OS cluster is up and running
* The user is authenticated to the cluster (`dcos auth login`)
* The cluster has sufficient resources to host the applications
* The following components are installed and running on the cluster:
  * Kafka
  * Spark
  * Cassandra (required only for KillrWeather app)
  * InfluxDB (required only for KillrWeather and network intrusion apps)
  * Grafana (required only for KillrWeather and network intrusion apps)

### Configuration file

The installation is driven through a JSON configuration file that defines the components that we want to install. The default file name is `bin/config.json.template`, which has the full set of components offered by the platform.

As a user you can customize this file to install a subset. Copy the file to `bin/config.json` and make the changes desired.

Here's part of the JSON template file for the network intrusion sample app. It shows that each entry consists of an application name along with the set of "components" available for installation for that particular application. Those "components" are actually deployed as Docker images:

```json
[
  {
    "app": "nwintrusion",
    "components": [
      "transform-data",
      "batch-k-means",
      "anomaly-detection"
    ]
  },
  ...
]
```

> **Note:** For any application, if the list of components is empty, then *all* components for that application are installed.

### Installation script

The installation script is `apps/bin/app-install.sh`. Here is the output of the command with the `--help` option:

```bash
$ apps/bin/app-install.sh --help

  apps/bin/app-install.sh

  Installs the sample applications of Lightbend Fast Data Platform.
  Assumes DC/OS authentication was successful using the DC/OS CLI.

  Usage: apps/bin/app-install.sh  [options]

  eg: apps/bin/app-install.sh --config-file ~/config.json

  Options:
  -f | --config-file file     Configuration file used to launch applications
                              Default: apps/bin/config.json
  -n | --no-exec              Do not actually run commands, just print them (for debugging).
  -h | --help                 Prints this message.
```
