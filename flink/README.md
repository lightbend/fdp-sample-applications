# Flink Sample Application

> **Disclaimer:** This sample application is provided as-is, without warranty. It is intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but it has not gone through a robust validation process, nor does it use all the techniques commonly employed for highly-resilient, production applications. Please use it with appropriate caution.

The sample application is adapted from the publicly-available [Flink training](http://dataartisans.github.io/flink-training/) from [dataArtisans](http://data-artisans.com/). It uses a public dataset of taxi rides in New York City. The details of the dataset can be found [here](http://dataartisans.github.io/flink-training/exercises/taxiData.html). In summary, the application does the following:

1. Load the dataset through a Marathon application
2. Read the dataset from a Kafka topic (`taxiin`)
3. Analyze and transform the dataset
4. Train a regression classifier for travel time prediction
5. Predict travel time based on model in Step 3
6. Write the prediction to another Kafka topic (`taxiout`)

The main components of running the Flink sample application are:

1. Deploy the data ingestion module as a Marathon app which will pull data from an S3 bucket and load it into a Kafka topic (`taxiin`)
2. Deploy travel time prediction application as a Marathon app which will predict the travel time after training from the classifier and write the output into a Kafka topic (`taxiout`)

## Running the applications Locally

All the applications can be run locally or on the DC/OS cluster using Marathon or Kubernetes.

`sbt` will be used to run applications on your local machine. The following examples demonstrate how to run the individual components from the `sbt` console.

### Running the Data Ingestion application

```
$ sbt
> projects
[info] In file:/Users/bucktrends/lightbend/fdp-sample-apps/flink/source/core/
[info] 	   fdp-flink-ingestion
[info] 	   fdp-flink-taxiride
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

    intopic = "taxiin"
    intopic = ${?KAFKA_IN_TOPIC}

    outtopic = "taxiout"
    outtopic = ${?KAFKA_OUT_TOPIC}

    zookeeper = "localhost:2181"
    zookeeper = ${?ZOOKEEPER_URL}

    ## settings for data ingestion
    loader {
      sourcetopic = ${dcos.kafka.intopic}
      sourcetopic = ${?KAFKA_IN_TOPIC}

      directorytowatch = "/Users/bucktrends/data"
      directorytowatch = ${?DIRECTORY_TO_WATCH}

      pollinterval = 1 second
    }
  }
}
```

All values can be set through environment variables as well. This is done when we deploy to the DC/OS cluster. For local running just change the settings to the values of your local environment.

### Running Taxi Ride application

```
$ sbt
> projects
[info] In file:/Users/bucktrends/lightbend/fdp-sample-apps/flink/source/core/
[info] 	   fdp-flink-ingestion
[info] 	   fdp-flink-taxiride
[info] 	   ingestRun
[info] 	 * root
> project fdp-flink-taxiride
> run --broker-list localhost:9092 --inTopic taxiin --outTopic taxiOut
```

## Deploying and running on DC/OS cluster

The first step in deploying the applications on DC/OS cluster is to prepare docker images of all the applications. This can be done from within sbt.

### Prepare docker images

In the `flink/source/core/` directory:

```
$ sbt
> projects
[info] 	   fdp-flink-ingestion
[info] 	   fdp-flink-taxiride
[info] 	   ingestRun
[info] 	 * root
> project fdp-flink-ingestion
> universal:packageZipTarball
> ...
> docker
```

This will create a docker image named `lightbend/fdp-flink-ingestion:X.Y.Z` (for the current version `X.Y.Z`) with the default settings. The name of the docker user comes from the `organization` field in `build.sbt` and can be changed there for alternatives. If the user name is changed, then the value of `$DOCKER_USERNAME` also needs to be changed in `flink/bin/app-install.sh`. The version of the image comes from `<PROJECT_HOME>/version.sh`. Change there if you wish to deploy a different version.

Once the docker image is created, you can push it to the repository at DockerHub.

Similarly we can prepare the docker image for the Taxi Ride Flink application. 

```
$ sbt
> projects
[info] 	   fdp-flink-ingestion
[info] 	   fdp-flink-taxiride
[info] 	   ingestRun
[info] 	 * root
> project fdp-flink-taxiride
> docker
```

> **One version of all applications will already be in lightbend Dockerhub as part of the platform release**

### Installing on DC/OS cluster

The installation scripts are present in the `flink/bin` folder. The script that you need to run is `app-install.sh` which takes a properties file as configuration. The default one is named `app-install.properties`.

```
$ pwd
.../flink/bin
$ ./app-install.sh --help
  Installs the NYC Taxi Ride sample app. Assumes DC/OS authentication was successful
  using the DC/OS CLI.

  Usage: app-install.sh   [options] 

  eg: ./app-install.sh 

  Options:
  --config-file               Configuration file used to lauch applications
                              Default: ./app-install.properties
  --start-none                Run no app, but set up the tools and data files.
  --start-only X              Only start the following apps:
                                ingestion      Performs data ingestion & transformation
                                app            Deploys the taxi travel time prediction app
                              Repeat the option to run more than one.
                              Default: runs all of them. See also --start-none.
  -n | --no-exec              Do not actually run commands, just print them (for debugging).
  -h | --help                 Prints this message.
$ ./app-install.sh --start-only ingestion --start-only app
```

This will start all 2 applications at once. In case you feel like, you can start them separately, especially if you are low on the cluster resource.

**Here are a few points that you need to keep in mind before starting the applications on your cluster:**

1. Need to have done dcos authentication beforehand. Run `dcos auth login`.
2. Need to have the cluster attached. Run `dcos cluster attach <cluster name>`.
3. Need to have Kafka and Flink running on the cluster.

Here's the default version of the configuration file that the installer uses:

```
## dcos kafka package
kafka-dcos-package=kafka

## dcos service name. Change this if you use a different service name in your
## Kafka installation on DC/OS cluster
kafka-dcos-service-name=kafka

## whether to skip creation of kafka topics - valid values : true | false
skip-create-topics=true

## kafka topic partition : default 1
kafka-topic-partitions=2

## kafka topic replication factor : default 1
kafka-topic-replication-factor=2

## security mode in cluster - valid values : strict | permissive | none
security-mode=permissive
```

## Removing the application from DC/OS

Just run `./app-remove.sh`.

It also has a `--help` option to show available command-line options. For example, use `--skip-delete-topics` if your cluster does not support deleting topics.

## Output of running the application

The computation results for travel time prediction appears in the Kafka topic `taxiout`. You can run a consumer and check the predicted times as they flow across during processing of the application.

## Deploying and running on Kubernetes

The first step in running applications on Kubernetes is the step of containerization, which we discussed in the last section. Once the docker images are built we can use Helm Charts to deploy the applications. 

All helm charts are created in the `bin/helm` folder of the respective application. Here's a sample of how to deploy all components of `taxiride` application into Kubernetes using the helm chart:

```
$ pwd
.../flink
$ cd bin
$ helm install --name taxiride ./helm
...
$ kubectl logs <pod name where the application runs>
```

Same technique can be used to deploy all the sample applications in Kubernetes.

