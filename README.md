# FDP Sample Applications

This repository contains the sample applications for [Lightbend Fast Data Platform](https://www.lightbend.com/products/fast-data-platform), version 1.3.0 and later. For information about these applications, see the [Fast Data Platform documentation](https://developer.lightbend.com/docs/fast-data-platform/current/), specifically the [Sample Applications](https://developer.lightbend.com/docs/fast-data-platform/current/user-guide/sample-apps/index.html) chapter. In order to run these applications as is, you must install them in a Fast Data Platform cluster. The [documentation](https://developer.lightbend.com/docs/fast-data-platform/current/) provides more details.

> **Disclaimer:** These sample applications are provided as-is, without warranty. They are intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but they have not gone through a robust validation process, nor do they use all the techniques commonly employed for highly-resilient, production applications. Please use them with appropriate caution.

There are branches in this repository that correspond to Fast Data Platform releases. For example, `release/1.3.0` for the 1.3.0 release. The `develop` branch is the "bleeding edge", which is usually fine to use, but if you encounter problems, consider using the release branch for your installation.

The applications are organized in folders with each of them containing details of how to use and install them as part of your environment. Here's the list of the applications:

* [KillrWeather](apps/killrweather/README.md): KillrWeather is a reference application adopted from the [original Datastax version](https://github.com/killrweather/killrweather), which shows how to easily leverage and integrate Apache Spark, Apache Cassandra, Apache Kafka, Akka, and InfluxDB for fast, streaming computations. This application focuses on the use case of time series data.

* [Model Server](apps/akka-kafka-streams-model-server/README.md): A sample application that demonstrates one way to update and serve machine learning models in a streaming context, using either Akka Streams or Kafka Streams.

* [Network Intrusion](apps/nwintrusion/README.md): A network intrusion detector application that ingests network traffic data from Kafka and runs an online clustering algorithm using Spark Streaming to detect anomalies.

* [VGG Training on CIFAR-10 data using BigDL](apps/bigdl/README.md): This is a demonstration of using a Spark based deep learning library on Fast Data Platform. We use [Intel BigDL](https://github.com/intel-analytics/BigDL) library and train a VGG Network on CIFAR-10 data set.

* [Taxiride Application using Flink](apps/flink/README.md): This is an adaptation of the publicly available [Flink training example from dataArtisans](http://training.data-artisans.com/). The application uses Flink as the streaming platform to train a regression classifier that predicts taxi travel times on a data set from New York City.

* [Processing Web Logs with Kafka Streams](apps/kstream/README.md): This example uses Kafka Streams APIs to process weblogs. It shows the power of both the higher level DSLs as well as the lower level Processor based APIs.

* [Anomaly Detection Model training and serving using Deep Learning](apps/anomaly-detection/README.md): This example demonstrates a deep learning based application running anomaly detection. It is a complete application consisting of many modules and includes the full lifecycle of data generation, ingestion, training, publishing and model serving.

## Installation

Each application contains detailed instructions on how to build and install the application locally or on a Fast Data Platform cluster, either on DC/OS, using the `dcos` CLI, or on Kubernetes or OpenShift, using [Helm](https://helm.sh/). See the individual READMEs for each application for details.

The following components must be installed and running on the cluster in advance (See the [Fast Data Platform documentation](https://developer.lightbend.com/docs/fast-data-platform/current/ for details):

* Kafka
* Spark
* Cassandra (required only for KillrWeather app)
* InfluxDB (required only for KillrWeather and network intrusion apps)
* Grafana (required only for KillrWeather and network intrusion apps)

For DC/OS this components can be installed from the the DC/OS catalog, for kubernetes/Openshift use the following 
[helm charts](/supportingcharts) and [documentation](/supportingcharts/README.md). 

### Kubernetes and OpenShift

For both platforms, [Helm](https://helm.sh/) is used to install the sample apps. Make sure the following prerequisites are installed first:

* The Fast Data Platform cluster is up and running
* The user is authenticated to the cluster
* The cluster has sufficient resources to host the applications
* The prerequisites mentioned above are installed.

Each sample application directory has a `helm` subdirectory with the corresponding Helm charts. Edit the files are desired and run

```bash
helm install apps/<app>/helm
```

### DC/OS

For installation into a Fast Data Platform DC/OS cluster, use the script `apps/bin/app-install.sh`, which calls individual `apps/<app>/bin/app-install.sh` scripts. Run the command `apps/bin/app-install.sh --help` for details on how to use it, such as how to specify which sample app(s) to run.

The following prerequisites are required first:

* A Fast Data Platform cluster is up and running
* The user is authenticated to the cluster (`dcos auth login`)
* The cluster has sufficient resources to host the applications
* The prerequisites mentioned above are installed.

> **Note:** The following procedures are NOT applicable for the deep learning based anomaly detection application. This is a complex application and the respective installation procedures are documented in the [README](apps/anomaly-detection/README.md) document of the project.

> **Note:** In order to run some of the Spark based applications on DC/OS e.g. Network Intrusion that uses DC/OS Spark CLI (`dcos spark run`) you need to have **ver 2.3.1-2.2.1-2-hadoop-2.6.5-01** from [Lightbend distribution of Spark](https://hub.docker.com/r/lightbend/spark/tags/).

#### DC/OS Configuration Files

The installation is driven through a JSON configuration file that defines the components that you want to install. A template for this file is provided, `apps/bin/config.json.template`, which has the full set of applications and the set of components for each application.

By default, the script will copy this file to `apps/bin/config.json` and use that copy. Or you can create your own copy first and edit it, such as removing the applications you don't want. You can also use a different file path, combined with the `--config-file` script argument.

Instead of editing the JSON file, you can also use the command-line option `--app app-name` to pick an application to run. Repeat these arguments to run several apps.

Here's part of the JSON template file for the network intrusion sample app. It shows that each entry consists of an application name along with the set of "components" available for installation for that particular application. Those "components" are actually deployed as Docker images:

```
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

