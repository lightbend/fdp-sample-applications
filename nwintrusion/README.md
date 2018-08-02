# FDP sample application for network intrusion detection

> **Disclaimer:** This sample application is provided as-is, without warranty. It is intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but it has not gone through a robust validation process, nor does it use all the techniques commonly employed for highly-resilient, production applications. Please use it with appropriate caution.

> **NOTE:** For a more complete version of these instructions, see the [online instructions](https://developer.lightbend.com/docs/fast-data-platform/latest/user-guide/developing-apps/index.html#streaming-k-means).
>
This application runs under DC/OS and has the following components that form stages of a pipeline:

1. **Data Ingestion:** The first stage reads data from a folder which is configurable and watchable. You can put new files in the folder and the file watcher will kickstart the data ingestion process. The first ingestion is however automatic and will be started 1 minute after the application installs.
2. **Data Transformation:** The second stage reads the data from the kafka topic populated in step 1, performs some transformations that will help in later stages of the data manipulation, and writes the transformed output into another Kafka topic. If there are any errors with specific records, these are recorded in a separate error Kafka topic. Stages 1 and 2 are implemented as a Kafka Streams application.
3. **Online Analytics and ML:** This stage of the pipeline reads data from the Kafka topic populated by stage 2, sets up a streaming context in Spark, and uses it to do streaming K-means clustering to detect network intrusion. A challenge is to determine the optimal value for K in a streaming context, i.e., by training the model, then testing with a different set of data. (More on this below.)
4. **An implementation of batch k-means:** Using this application, the user can iterate on the number of clusters (`k`) that should be used for the online anomaly detection part. The application accepts a batch duration and for all data that it receives in that duration it runs k-means clustering in batch for all values of `k` that fall within the range as specified by the user. The user can specify the starting and ending values of `k` and the increment step size as command line arguments and the application will run k-means for the entire range and report the cluster score (mean squared error). The optimal value of `k` can then be found using the elbow method.

*In the current implementation, Stages 1 and 2 are packaged together in a single application that runs under Marathon.*


## Data for the application

The application uses the dataset from [KDD Cup 1999](https://kdd.ics.uci.edu/databases/kddcup99/kddcup99.html), which asked competitors to develop a network intrusion detector. The reason for using this data set is that it makes a good case study for clustering and intrusion detection is a common use for streaming platforms like FDP.

## Running the applications Locally

All the applications can be run locally or on the DC/OS cluster using Marathon or Kubernetes.

> **Note:** Kubernetes support is currently experimental and the approach can change in the future versions.

`sbt` will be used to run applications on your local machine. The following examples demonstrate how to run the individual components from the `sbt` console.

### Running the Data Ingestion and Transformation application

```
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

This will run the data ingestion and transformation application on the local machine. Before running the application, please ensure the configuration files are set up appropriately for the local environment. Here's the default setup of `application.conf` within the `ingestion` folder of the project:

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

All values can be set through environment variables as well. This is done when we deploy to the DC/OS cluster. For local running just change the settings to the values of your local environment.

### Running Anomaly Detection application

```
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
The `--master` argument is optional and will default to `local[*]`. There is another optional argument `--with-influx` which uses Influx DB and Grafana to store and display anomalies. In case you decide to use this option, you need to set up the influx configuration by changing appropriate settings in the config file `application.conf` within `anomaly` folder of the project based on your local environment. Here's the default settings of the file:

```
visualize {
  influxdb {
    server = "http://influxdb.marathon.l4lb.thisdcos.directory"
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
    server="grafana.marathon.l4lb.thisdcos.directory"
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

### Running Batch K-Means application

```
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
The `--master` argument is optional and will default to `local[*]`.

## Deploying and running on DC/OS cluster

The first step in deploying the applications on DC/OS cluster is to prepare docker images of all the applications. This can be done from within sbt.

### Prepare docker images

In the `nwintrusion/source/core/` directory:

```
$ sbt
> projects
[info] 	   fdp-nwintrusion-anomaly
[info] 	   fdp-nwintrusion-batchkmeans
[info] 	   fdp-nwintrusion-ingestion
[info] 	   ingestRun
[info] 	 * root
> project fdp-nwintrusion-ingestion
> universal:packageZipTarball
> ...
> docker
```

This will create a docker image named `lightbend/fdp-nwintrusion-ingestion:X.Y.Z` (for the current version `X.Y.Z`) with the default settings. The name of the docker user comes from the `organization` field in `build.sbt` and can be changed there for alternatives. If the user name is changed, then the value of `$DOCKER_USERNAME` also needs to be changed in `nwintrusion/bin/utils.sh`. The version of the image comes from `<PROJECT_HOME>/version.sh`. Change there if you wish to deploy a different version.

Once the docker image is created, you can push it to the repository at DockerHub.

Similarly we can prepare the docker images for the Spark applications. For Spark applications we need to build separate docker images for DC/OS as well as Kubernetes. The images for the 2 will be different.

**In order to build docker images for Kubernetes you need to set the system property named `K8S_OR_DCOS` to `K8S` or else the image will be built for DC/OS.**

```
$ sbt
> projects
[info] In file:/Users/bucktrends/lightbend/fdp-sample-apps/nwintrusion/source/core/
[info] 	   fdp-nwintrusion-anomaly
[info] 	   fdp-nwintrusion-batchkmeans
[info] 	   fdp-nwintrusion-ingestion
[info] 	   ingestRun
[info] 	 * root
> project fdp-nwintrusion-anomaly
> docker ## build docker image for DC/OS
> ...
> project fdp-nwintrusion-batchkmeans
> docker ## build docker image for DC/OS
```

```
$ sbt -DK8S_OR_DCOS=K8S
> projects
[info] In file:/Users/bucktrends/lightbend/fdp-sample-apps/nwintrusion/source/core/
[info] 	   fdp-nwintrusion-anomaly
[info] 	   fdp-nwintrusion-batchkmeans
[info] 	   fdp-nwintrusion-ingestion
[info] 	   ingestRun
[info] 	 * root
> project fdp-nwintrusion-anomaly
> docker ## build docker image for K8S
> ...
> project fdp-nwintrusion-batchkmeans
> docker ## build docker image for K8S
```

The image built for Kubernetes will be named with a suffix `-k8s`, e.g. for anomaly detection, the image will be named `lightbend/fdp-nwintrusion-anomaly-k8s:X.Y.Z`, while the one for DC/OS will not have the suffix. Here's an example:

```
$ docker images
REPOSITORY                           TAG                        IMAGE ID            CREATED             SIZE
lightbend/fdp-nwintrusion-anomaly-k8s       X.Y.Z                      982adc4c3ae9        42 minutes ago      401MB
lightbend/fdp-nwintrusion-anomaly           X.Y.Z                      5b43990ebfe6        7 hours ago         1.48GB

```

### Installing on DC/OS cluster

The installation scripts are present in the `nwintrusion/bin` folder. The script that you need to run is `app-install.sh` which takes a properties file as configuration. The default one is named `app-install.properties`.

```
$ pwd
.../nwintrusion/bin
$ ./app-install.sh --help
  Installs the network intrusion app. Assumes DC/OS authentication was successful
  using the DC/OS CLI.

  Usage: app-install.sh   [options]

  eg: ./app-install.sh

  Options:
  --config-file               Configuration file used to lauch applications
                              Default: ./app-install.properties
  --start-none                Run no app, but set up the tools and data files.
  --start-only X              Only start the following apps:
                                transform-data      Performs data ingestion & transformation
                                batch-k-means       Find the best K value.
                                anomaly-detection   Find anomalies in the data.
                              Repeat the option to run more than one.
                              Default: runs all of them. See also --start-none.
  -n | --no-exec              Do not actually run commands, just print them (for debugging).
  -h | --help                 Prints this message.
$ ./app-install.sh --start-only transform-data --start-only anomaly-detection --start-only batch-k-means
```

This will start all 3 applications at once. In case you feel like, you can start them separately, especially if you are low on the cluster resource.

**Here are a few points that you need to keep in mind before starting the applications on your cluster:**

1. Need to have done dcos authentication beforehand. Run `dcos auth login`.
2. Need to have the cluster attached. Run `dcos cluster attach <cluster name>`.
3. Need to have Kafka, Spark, InfluxDB and Grafana running on the cluster.

Here's the default version of the configuration file that the installer uses:

```
## dcos kafka package
kafka-dcos-package=kafka

## dcos service name. Change this if you use a different service name in your
## Kafka installation on DC/OS cluster
kafka-dcos-service-name=kafka

## whether to skip creation of kafka topics - valid values : true | false
skip-create-topics=false

## kafka topic partition : default 1
kafka-topic-partitions=1

## kafka topic replication factor : default 1
kafka-topic-replication-factor=1
```
> **One version of all application images will already be in lightbend Dockerhub as part of the platform release**

## Removing the application from DC/OS

Just run `./app-remove.sh`.

It also has a `--help` option to show available command-line options. For example, use `--skip-delete-topics` if your cluster does not support deleting topics.


## Visualization of Anomaly Detection

Anomaly detection application stores possible anomalies in an InfluxDB database, which can be visualized using Grafana. The data source is set up by the application for integrating with Grafana. Just log in to Grafana using the credentials `admin/admin` and proceed to the dashboard named *NetworkIntrusion*.

**N.B.** In case you plan to re-deploy the anomaly detection application, you will need to manually remove the data source and dashboard from Grafana.

## Deploying and running on Kubernetes

The first step in running applications on Kubernetes is the step of containerization, which we discussed in the last section. Once the docker images are built we can use Helm Charts to deploy the applications. For Spark applications, the images that have a name suffixed with `-k8s` need to be deployed.

All helm charts are created in the `bin/helm` folder of the respective application. Here's a sample of how to deploy all components of `nwintrusion` application into Kubernetes using the helm chart:

```
$ pwd
.../nwintrusion
$ cd bin
$ helm install --name nwintrusion ./helm
...
$ kubectl logs <pod name where the application runs>
```

Same technique can be used to deploy all the sample applications in Kubernetes.
