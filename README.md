# FDP Sample Applications

> **DISCLAIMER:** These sample applications are provided as-is, without warranty. They are intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but they have not gone through a robust validation process, nor do they use all the techniques commonly employed for secure, highly-resilient, production applications. Please use them with appropriate caution.

> **WARNING:** The `develop` branch works only for Fast Data Platform V2.X on OpenShift. To use these sample applications on Fast Data Platform V1.X on DC/OS, use the branch corresponding to your release (e.g., `release/1.3.2`) or `develop-DCOS` for the latest updates. For a particular OpenShift release, look at branches of the form `release/2.0.0-OpenShift`, for example.

This repository contains the sample applications for [Lightbend Fast Data Platform](https://www.lightbend.com/products/fast-data-platform), version 1.3.0 and later. For information about these applications, see the [Fast Data Platform documentation](https://developer.lightbend.com/docs/fast-data-platform/current/), specifically the [Sample Applications](https://developer.lightbend.com/docs/fast-data-platform/current/#sample-apps) section. In order to run these applications as is, you must install them in a Fast Data Platform cluster. The [documentation](https://developer.lightbend.com/docs/fast-data-platform/current/) provides more details.

> **Note:** At the time of this release, some of the applications were not yet completely ported to OpenShift and Kubernetes from DC/OS. They are the `bigdl`, `nwintrusion`, and `kstreams` apps. Each app's README describes what is already working. A future release of Fast Data Platform for OpenShift and Kubernetes will remove this limitation.

The applications are organized in folders with each of them containing details of how to use and install them as part of your environment. Here's the list of applications:

* [KillrWeather](apps/killrweather/README.md): KillrWeather is a reference application adopted from the [original Datastax version](https://github.com/killrweather/killrweather), which shows how to easily leverage and integrate Apache Spark, Apache Cassandra, Apache Kafka, Akka, and InfluxDB for fast, streaming computations. This application focuses on the use case of time series data.

* [Anomaly Detection Model training and serving using Deep Learning](apps/anomaly-detection/README.md): This example demonstrates a deep learning based application running anomaly detection. It is a complete application consisting of many modules and includes the full lifecycle of data generation, ingestion, training, publishing and model serving.

* [Akka, Kafka Streams, and Kafka-based Model Server](apps/akka-kafka-streams-model-server/README.md): A sample application that demonstrates one way to update and serve machine learning models in a streaming context, using either Akka Streams or Kafka Streams with Kafka used for data exchange.

* [Network Intrusion](apps/nwintrusion/README.md): A network intrusion detector application that ingests network traffic data from Kafka and runs an online clustering algorithm using Spark Streaming to detect anomalies.

* [VGG Training on CIFAR-10 data using BigDL](apps/bigdl/README.md): This is a demonstration of using a Spark based deep learning library on Fast Data Platform. We use [Intel BigDL](https://github.com/intel-analytics/BigDL) library and train a VGG Network on CIFAR-10 data set.

* [Taxiride Application using Flink](apps/flink/README.md): This is an adaptation of the publicly available [Flink training example from dataArtisans](http://training.data-artisans.com/). The application uses Flink as the streaming platform to train a regression classifier that predicts taxi travel times on a data set from New York City.

* [Processing Web Logs with Kafka Streams](apps/kstream/README.md): This example uses the Kafka Streams APIs to process weblogs. It shows the power of both the higher level DSLs as well as the lower level Processor based APIs.

## General Structure of this Repo

This repo is organized as follows:

* `LICENSE` - Apache 2.0 license
* `README.md` - This README
* `apps` - location of the READMEs, source code, build files, etc. for the apps
* `build.sh` - Global CI build script used by Lightbend, which you can use, too, if you want to build some or all of the applications yourself. Run `build.sh --help` for details and discussion below.
* `process-templates.sh` - Sets the version string in templated config files; see `version.sh`.
* `release` - Lightbend CI scripts for our builds. You can safely ignore this directory ;)
* `supportingcharts` - Helm charts for installing third-party services used by the sample apps.
* `version.sh` - The global version used to build the application artifacts, like Docker images.

### General Structure for each Application

The directory structure for each application includes some or all of the following contents:

* `data` - Data used by the app.
* `helm*` - Helm charts for installing in OpenShift or Kubernetes.
* `images` and `diagrams` - For the README.
* `manager-support` - Definitions for integration with Lightbend Fast Data Platform Manager. You can ignore this directory; this data is already incorporated into the Manager.
* `release` - Part of the Lightbend CI toolchain.
* `source/core` - The SBT project root, with the build files, source code, etc.
* `test` - Other, non-unit tests.
* `build.sh` and `Jenkinsfile` - Used for Lightbend's CI system. This is the easiest way to build individual apps, which drive the appropriate SBT tasks. Note that each takes an argument `--push-docker-images`, which will push any Docker images to Docker Hub! Hence, you'll need to change the `.../build.sbt` file in each app to point to your Docker Hub account or other compatible repository, if you use this option. The app READMEs provide more specific details.
* `README.md` - Details about building and running this app.

In a few cases, when the app contains several services (e.g., `akka-kafka-streams-model-server`), each service has its own directory instead of being nested under `source/core`. These projects will also have `build.sbt` and `project/` SBT files in the root directory, whereas they are under `source/core` for the other apps. In all cases, the corresponding `build.sh` knows what to do...

## Installing the Prerequisites

Make sure the following prerequisites are installed first:

* The Fast Data Platform cluster is up and running
* The user is authenticated to the cluster
* The cluster has sufficient resources to host the applications
* The following prerequisites are installed

These [supported](https://developer.lightbend.com/docs/fast-data-platform/current/#overview) components must be installed and running on the cluster in advance:

* Kafka
* Spark

The Fast Data Platform [installation instructions](https://developer.lightbend.com/docs/fast-data-platform/current/#installation) provide detailed instructions.

In addition, several [certified](https://developer.lightbend.com/docs/fast-data-platform/current/#overview) components are needed by some of the applications:

* Cassandra: required for KillrWeather
* InfluxDB: required for KillrWeather, anomaly detection, and network intrusion
* Grafana: required for KillrWeather, anomaly detection, and network intrusion

(Additional optional, _certified_ components are described by each component's documentation.)

Use the Helm charts in [`supportingcharts`](/supportingcharts) to install these _certified_ components. See its [README](/supportingcharts/README.md) for details. (For DC/OS installations, see the appropriate branch of this repository, as discussed above.)

## Installing the Applications

Each application contains detailed instructions on how to build and run the application locally or on a Fast Data Platform cluster on OpenShift or Kubernetes using [Helm](https://helm.sh/). See the individual READMEs for each application for details.

[Helm](https://helm.sh/) is used to install the sample apps.

Each sample application directory has a `helm` subdirectory with the corresponding Helm charts. Edit the files as desired and run the following command (from this "root" directory):

```bash
helm install apps/<app>/helm
```

Some of the applications (e.g., the deep learning-based anomaly detection application, `anomaly-detection`) have more complex installation steps. Consult each application's `README.md` for specific details.

## Building the Applications Yourself

As discussed above, there is a top-level `build.sh` that will build all the applications:

```bash
./build.sh
```

This script builds the archives, Docker images, and updates the Helm charts with the current version set in `version.sh`. Pass the `--help` option for command-line options. For example, the version can be overridden with `--version 1.2.3`.

This script does global setup, like generating YAML files from templates with the correct version string, but you can also build each application individually, as all of them have their own `build.sh` and each one also has a `--help` option.

These scripts are the easiest way to build individual apps, which drive the appropriate `sbt` tasks. Note that each one accepts an argument `--push-docker-images`, which will push any Docker images to Docker Hub! Hence, you'll need to change the `.../build.sbt` file in each app to point to your Docker Hub account or other compatible repository, if you use this option. The app READMEs provide more specific details.

> **Note:** Obviously `bash` is required for these scripts. If you're on Windows without access to `bash`, you can run the `sbt` and `docker` commands directly that these shell scripts invoke to build the artifacts, etc.

