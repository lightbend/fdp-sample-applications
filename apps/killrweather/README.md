# KillrWeather

KillrWeather is a reference application that is adopted from Datastax's https://github.com/killrweather/killrweather. It shows how to easily leverage and integrate [Apache Spark](http://spark.apache.org), [Apache Cassandra](http://cassandra.apache.org), [Apache Kafka](http://kafka.apache.org), [Akka](https://akka.io), and [InfluxDB](https://www.influxdata.com/) for fast, streaming computations. This application focuses on the use case of [time series data](https://github.com/killrweather/killrweather/wiki/4.-Time-Series-Data-Model), specifically weather data.

This application also can be viewed as a prototypical IoT (e.g., sensors) data collection application, which stores data in the form of a time series.

> **DISCLAIMER:** This sample application is provided as-is, without warranty. It is intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but it has not gone through a robust validation process, nor does it use all the techniques commonly employed for highly-resilient, production applications. Please use it with appropriate caution.

## Sample Use Case

_I need fast access to historical data on the fly for predictive modeling with real time data from the stream._

The application does not quite do that, it stops at capturing real-time and cumulative information.

## Reference Application

There are several versions this application:
* [KillrWeather App](https://github.com/lightbend/fdp-sample-applications/tree/develop/apps/killrweather/source/core/killrweather-app/) is based on Spark Streaming.
* [KillrWeather App Local](https://github.com/lightbend/fdp-sample-applications/tree/develop/apps/killrweather/source/core/killrweather-app-local/) provides configuration files for running locally.
* [KillrWeather App Structured](https://github.com/lightbend/fdp-sample-applications/tree/develop/apps/killrweather/source/core/killrweather-app_structured/) is a version of `killrweather-app`, but using Spark Structured Streaming.
* [KillrWeather Beam](https://github.com/lightbend/fdp-sample-applications/tree/develop/apps/killrweather/source/core/killrweather-app-beam/) experimental version of the same application based on [Apache Beam](https://beam.apache.org/). This version only runs locally, using embedded Kafka. A cluster version is coming soon.

There are a few other variants of these apps, as well as support apps for data loading, etc., as discussed below.

## Time Series Data

The use of time series data for business analysis is not new. What is new is the ability to collect and analyze massive volumes of data in sequence at extremely high velocity to get the clearest picture to predict and forecast future market changes, user behavior, environmental conditions, resource consumption, health trends and much, much more.

Apache Cassandra is a NoSQL database platform particularly suited for these types of Big Data challenges. Cassandra’s data model is an excellent fit for handling data in sequence regardless of data type or size. When writing data to Cassandra, data is sorted and written sequentially to disk. When retrieving data by row key and then by range, you get a fast and efficient access pattern due to minimal disk seeks – time series data is an excellent fit for this type of pattern. Apache Cassandra allows businesses to identify meaningful characteristics in their time series data as fast as possible to make clear decisions about expected future outcomes.

There are many flavors of time series data. Some are windows into the stream, others cannot be windows, because the queries are not by time slice but by specific year, month, day, hour, etc. Spark Streaming lets you do both.

In addition to Cassandra, the application also demonstrates integration with InfluxDB and Grafana. This toolset is typically used for DevOps monitoring purposes, but it can also be very useful for real-time visualization of stream processing. Data is written to influxDB as it gets ingested in the application and Grafana is used to provide a real time view of temperature, pressure, and dewpoint values, as the reports come in. Additionally, the application provides results of data rollups - daily and monthly mean, low, and high values.

## For More Background Information

The original [KillrWeather Wiki](https://github.com/killrweather/killrweather/wiki) is still a great source of background information.

## Using the Applications

We foresee two groups of users:

* Users who just want to see how application runs. We provide prebuilt Docker images.
* Users who want to use this project as a starting point for their own applications, so they'll want to build the code themselves.

## Building and Configuring the Applications

The Docker images already exist, but if you want to build the applications yourself.

First, run the following script to generate the config files from the templates. The substitutions made include using an appropriate value for `VERSION`, e.g., `1.2.0`:

```bash
./process-templates.sh VERSION
```

Building the app can be done using the convenient `build.sh` or `sbt`.

For `build.sh`, use one of the following commands:

```bash
build.sh
build.sh --push-docker-images
```

Both effectively run `sbt clean compile docker`, while the second variant also pushes the images to your Docker Hub account. _Only use this option_ if you first change `organization in ThisBuild := CommonSettings.organization` to `organization in ThisBuild := "myorg"` in `source/core/build.sbt`!

You can also use `sbt` itself. First change to the `source/core` directory.

```bash
$ sbt
sbt:killrweather> projects
...
[info]      appLocalRunner
[info]      appLocalRunnerstructured
[info]      fdp-killrweather-app
[info]      fdp-killrweather-grpcclient
[info]      fdp-killrweather-httpclient
[info]      fdp-killrweather-loader
[info]      fdp-killrweather-structured-app
[info]      killrWeatherApp_beam
[info]      killrWeatherCore
[info]    * killrweather
[info]      protobufs
sbt:killrweather> docker
```

You can use the `sbt` target `dockerPush` to push the images to Docker Hub, but only after changing the `organization` as just described. You can publish to your local (machine) repo with the `docker:publishLocal` target.

For IDE users, just import a project and use IDE commands, but it is necessary to run `sbt clean compile` at least once to compile the `protobufs` subproject correctly.

## Installing KillrWeather on OpenShift or Kubernetes

These are step by step instructions for installing Killrweather on OpenShift or Kubernetes. We'll use the OpenShift CLI command `oc`, but substitute with `kubectl` for Kubernetes.

The installation is done in a separate project, which we'll call `sample`. First create it, if it does not exist:

```bash
oc new-project sample
```

### Installing Prerequisites

Killrweather depends on the following components:
* Kafka for communications
* Cassandra for final storage
* NFS for Spark checkpointing
* InfluxDB for real time data storage
* Grafana for viewing real time results
* Zeppelin for viewing of the content of Cassandra

Follow [this documentation](../../supportingcharts/README.md) for installation of these components.

### Installing Killrweather

The PVC plus GlusterFS [Helm chart](helm-pvc) has to be extended with the role and role binding definitions:

```yaml
apiVersion: rbac.authorization.k8s.io/v1beta1
kind: ClusterRole
metadata:
  name: spark-role
rules:
  - apiGroups:
  - ""
  resources: ["pods" , "services", "configmaps" ]
  verbs:
  - "*"
---
apiVersion: rbac.authorization.k8s.io/v1beta1
kind: ClusterRoleBinding
metadata:
   name: spark-role-default-binding
   subjects:
   - kind: ServiceAccount
   name: default
   namespace: sample
roleRef:
   kind: ClusterRole
   name: spark-role
   apiGroup: rbac.authorization.k8s.io
```

These settings grant containers the ability to watch for pods.

After this is added, the chart deploys the `killrweather` application.

This Helm chart uses [GlusterFS](https://docs.gluster.org/en/v3/Administrator%20Guide/GlusterFS%20Introduction/)) as the backing store for checkpointing. To use HDFS for Spark checkpointing, use the HDFS [Helm chart](helm-hdfs).

> **Note:** One of the things that make OpenShift “special” is a more rigorous requirement to placing ‘ around content of --conf content. Without ‘ execution of config is unpredictable.

### To See What's Going On...

Go to the Spark Web console for this job at http://host:4040/jobs/, for either the Spark Streaming or Structured Streaming versions of the app.

## Loading Data

The application can use three different loaders, which are local client applications that can communicate with the corresponding cluster services or clients to send weather reports.:

1. Direct Kafka loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngester` pushes data directly to the Kafka queue that an application is listening on.
2. HTTP loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngesterRest` writes data to KillrWeather HTTP client.
3. GRPC loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngesterGRPC` writes data to KillrWeather GRPC client.

Use the commands we saw previously for data loading to run these commands locally.

## Monitoring and Viewing Results

Monitoring is done using InfluxDB and Grafana.

For information about setting up Grafana, InfluxDB, Cassandra, and optionally Zeppelin, see the [project's README](../../README.md).

To open the Grafana UI, click the `grafana` service in the OpenShift or Kubernetes GUI. The login credentials are configured to be `admin`/`admin` for simplicity.

In the Grafana UI, load the definitions in `./killrweather-app/src/main/resource/grafana.json`. (Click the upper-left-hand side Grafana icon, then _Dashboards_, then _Import_.) This will create a dashboard called _KillrWeather Data Ingestion_.

Once set up and once data is flowing through the system, you can view activity in this dashboard.

Applications themselves currently implement setup of databases in Cassandra (if present) and InfluxDB and dashboards in Grafana. So this information is here just for reference.

To view execution results that are stored in Cassandra, a Zeppelin notebook can be used, configured for [Cassandra in Zeppelin](https://zeppelin.apache.org/docs/0.7.2/interpreter/cassandra.html).

After installing Zeppelin, configure it for use with the Cassandra SQL interpreter (available already as a Zeppelin plugin in the package). The most important settings are these:

```
name	                   value
cassandra.cluster        cassandra
cassandra.hosts	         <host_name>
cassandra.native.port	   9042
```

(for the appropriate `<host_name>`).

Create a notebook. Try the following, one per notebook cell:

```
%cassandra
use isd_weather_data;

%cassandra
select day, wsid, high, low, mean,stdev,variance from daily_aggregate_temperature WHERE year=2008 and month=6 allow filtering;

%cassandra
select wsid, day, high, low, mean,stdev,variance from daily_aggregate_pressure  WHERE year=2008 and month=6 allow filtering;

%cassandra
select wsid, month, high, low, mean,stdev,variance from monthly_aggregate_temperature WHERE year=2008  allow filtering;

%cassandra
select wsid, month, high, low, mean,stdev,variance from monthly_aggregate_pressure WHERE year=2008  allow filtering;

```
