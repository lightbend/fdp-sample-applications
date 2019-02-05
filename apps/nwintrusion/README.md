# Network Intrusion Detection Sample Application

> **DISCLAIMER:** This sample application is provided as-is, without warranty. It is intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but it has not gone through a robust validation process, nor does it use all the techniques commonly employed for highly-resilient, production applications. Please use it with appropriate caution.

This application has the following components that form stages of a pipeline:

1. **Data Ingestion:** The first stage reads data from a folder which is configurable and watchable. You can put new files in the folder and the file watcher will kickstart the data ingestion process. The first ingestion is however automatic and will be started 1 minute after the application installs.
2. **Data Transformation:** The second stage reads the data from the kafka topic populated in step 1, performs some transformations that will help in later stages of the data manipulation, and writes the transformed output into another Kafka topic. If there are any errors with specific records, these are recorded in a separate error Kafka topic. Stages 1 and 2 are implemented as a Kafka Streams application.
3. **Online Analytics and ML:** This stage of the pipeline reads data from the Kafka topic populated by stage 2, sets up a streaming context in Spark, and uses it to do streaming K-means clustering to detect network intrusion. A challenge is to determine the optimal value for K in a streaming context, i.e., by training the model, then testing with a different set of data. (More on this below.)
4. **An implementation of batch k-means:** Using this application, the user can iterate on the number of clusters (`k`) that should be used for the online anomaly detection part. The application accepts a batch duration and for all data that it receives in that duration it runs k-means clustering in batch for all values of `k` that fall within the range as specified by the user. The user can specify the starting and ending values of `k` and the increment step size as command line arguments and the application will run k-means for the entire range and report the cluster score (mean squared error). The optimal value of `k` can then be found using the elbow method.

*In the current implementation, Stages 1 and 2 are packaged together in a single application that runs in the same pod.*

## Data for the Application

The application uses the dataset from [KDD Cup 1999](https://kdd.ics.uci.edu/databases/kddcup99/kddcup99.html), which asked competitors to develop a network intrusion detector. The reason for using this data set is that it makes a good case study for clustering and intrusion detection is a common use for streaming platforms like Fast Data Platform.

## Running the Applications Locally

All the applications can be run locally or on OpenShift or Kubernetes.

> **Note:** Kubernetes or OpenShift support is currently experimental and the approach used may change in future releases of Fast Data Platform.

For local execution, `sbt` is used to run the applications. The following examples demonstrate how to run the individual components from the `sbt` console.

### Running the Data Ingestion and Transformation application

```bash
$ sbt
> projects
[info] In file:/Users/bucktrends/lightbend/fdp-sample-apps/nwintrusion/source/core/
[info] 	   fdp-nwintrusion-anomaly
[info] 	   fdp-nwintrusion-batchkmeans
[info] 	   fdp-nwintrusion-ingestion
[info] 	   ingestRun
[info] 	 * root
> project ingestRun
> ingest
```

These commands switch to the nested `ingestRun` project, then run the data ingestion and transformation application locally. Before running the application, please ensure the configuration files are set up appropriately for the local environment. Here's the default setup of `application.conf` within the `ingestion` folder of the project:

```
dcos {

  kafka {
    brokers = "localhost:9092,localhost:9093,localhost:9094"
    brokers = ${?KAFKA_BROKERS}

    group = "group"
    group = ${?KAFKA_GROUP}

    fromtopic = "nwin"
    fromtopic = ${?KAFKA_FROM_TOPIC}

    totopic = "nwout"
    totopic = ${?KAFKA_TO_TOPIC}

    errortopic = "nwerr"
    errortopic = ${?KAFKA_ERROR_TOPIC}

    zookeeper = "localhost:2181"
    zookeeper = ${?ZOOKEEPER_URL}

    ## settings for data ingestion
    loader {
      sourcetopic = ${dcos.kafka.fromtopic}
      sourcetopic = ${?KAFKA_FROM_TOPIC}

      directorytowatch = "/Users/ingest-data"
      directorytowatch = ${?DIRECTORY_TO_WATCH}

      pollinterval = 1 second
    }
  }
}
```

All values can be set through environment variables as well. This is done when we deploy to cluster environments. For running locally, just change the settings to the values of your local environment.

### Running the Anomaly Detection application

```bash
$ sbt
> projects
[info] In file:/Users/bucktrends/lightbend/fdp-sample-apps/nwintrusion/source/core/
[info] 	   fdp-nwintrusion-anomaly
[info] 	   fdp-nwintrusion-batchkmeans
[info] 	   fdp-nwintrusion-ingestion
[info] 	   ingestRun
[info] 	 * root
> project fdp-nwintrusion-anomaly
> run --master local[*] --read-topic nwout --kafka-broker localhost:9092 --micro-batch-secs 60 --cluster-count 100
```

The `--master` argument (for Spark) is optional and will default to `local[*]`. There is another optional argument `--with-influx` which uses InfluxDB and Grafana to store and display anomalies. If you decide to use this option, you need to set up the InfluxDB configuration first by changing the appropriate settings in the config file `application.conf` within the `anomaly` folder of the project, based on your local environment. Here are the default settings in this file:

```
visualize {
  influxdb {
    # server = "http://localhost"
    server = ${?INFLUXDB_SERVER}

    port = 8086
    port = ${?INFLUXDB_PORT}

    user = "root"
    user = ${?INFLUXDB_USER}

    password = "root"
    password = ${?INFLUXDB_PASSWORD}

    database = "anomaly"
    database = ${?INFLUXDB_DATABASE}

    retentionPolicy = "default"
    retentionPolicy = ${?INFLUXDB_RETENTION_POLICY}
  }

  grafana {
    # server="localhost"
    server=${?GRAFANA_SERVER}

    port=3000
    host=${?GRAFANA_PORT}

    user = "admin"
    user = ${?GRAFANA_USER}

    password = "admin"
    password = ${?GRAFANA_PASSWORD}
  }
}
```

### Running Batch K-Means Application

```bash
$ sbt
> projects
[info] In file:/Users/bucktrends/lightbend/fdp-sample-apps/nwintrusion/source/core/
[info] 	   fdp-nwintrusion-anomaly
[info] 	   fdp-nwintrusion-batchkmeans
[info] 	   fdp-nwintrusion-ingestion
[info] 	   ingestRun
[info] 	 * root
> project fdp-nwintrusion-batchkmeans
> run --master local[*] --read-topic nwout --kafka-broker localhost:9092 --micro-batch-secs 60 --from-cluster-count 40 --to-cluster-count 100 --increment 10
```

The `--master` argument (for Spark) is optional and will default to `local[*]`.

## Deploying and Running on OpenShift or Kubernetes

You can use Lightbend's prebuilt Docker images or build your own.

Building the app can be done using the convenient `build.sh` or `sbt`.

For `build.sh`, use one of the following commands:

```bash
build.sh
build.sh --push-docker-images
```

Both effectively run `sbt clean compile docker`, while the second variant also pushes the images to your Docker Hub account. _Only use this option_ if you first change `organization in ThisBuild := CommonSettings.organization` to `organization in ThisBuild := "myorg"` in `source/core/build.sbt`!

You can also build directly with `sbt` commands. For historical reasons, there is a system property named `K8S_OR_DCOS` that configures the build for OpenShift/Kubernetes or DC/OS. Set the value to `K8S`. (This is done in `build.sh` for you.)

Now change to the `nwintrusion/source/core/` directory and run these commands:

```bash
$ pwd
.../kstream/source/core
$ sbt -DK8S_OR_DCOS=K8S
> projects
[info] 	   fdp-nwintrusion-anomaly
[info] 	   fdp-nwintrusion-batchkmeans
[info] 	   fdp-nwintrusion-ingestion
[info] 	   ingestRun
[info] 	 * root
> docker
```

This will create Docker images named `lightbend/fdp-nwintrusion-batchkmeans-k8s:X.Y.Z` and `lightbend/fdp-nwintrusion-anomaly-k8s:X.Y.Z` for the current version `X.Y.Z`. The name of the Docker user (`lightbend`) comes from the `organization` field in `build.sbt` and must be changed if you intend to upload your images to a public repo.

You can use the `sbt` target `dockerPush` to push the images to Docker Hub, but only after changing the `organization` as just described. You can publish to your local (machine) repo with the `docker:publishLocal` target.

For IDE users, just import a project and use IDE commands.

You can also create individual Docker images for the specific nested projects (Spark applications), for example:

```bash
$ sbt -DK8S_OR_DCOS=K8S
> projects
[info] In file:/Users/bucktrends/lightbend/fdp-sample-apps/nwintrusion/source/core/
[info] 	   fdp-nwintrusion-anomaly
[info] 	   fdp-nwintrusion-batchkmeans
[info] 	   fdp-nwintrusion-ingestion
[info] 	   ingestRun
[info] 	 * root
> project fdp-nwintrusion-anomaly
> docker
> ...
> project fdp-nwintrusion-batchkmeans
> docker
```

## Deploying and Running on OpenShift or Kubernetes

Use the Helm Charts in the `helm` directory to deploy the applications. The following commands deploy all the components of `nwintrusion` into OpenShift or Kubernetes:

```bash
$ pwd
.../nwintrusion
$ helm install --name nwintrusion ./helm
...
$ kubectl logs <pod name where the application runs>
```

## Visualization of Anomaly Detection Results

The Anomaly detection application stores possible anomalies in an InfluxDB database, which can be visualized using Grafana. The data source is set up by the application for integrating with Grafana. Just log in to Grafana using the credentials `admin/admin` and proceed to the dashboard named *NetworkIntrusion*.

**N.B.** In case you plan to re-deploy the anomaly detection application, you will need to manually remove the data source and dashboard from Grafana.
