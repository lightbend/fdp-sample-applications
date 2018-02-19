# FDP sample application for network intrusion detection

> **Disclaimer:** This sample application is provided as-is, without warranty. It is intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but it has not gone through a robust validation process, nor does it use all the techniques commonly employed for highly-resilient, production applications. Please use it with appropriate caution.

> **NOTE:** For a more complete version of these instructions, see the [online instructions](https://developer.lightbend.com/docs/fast-data-platform/0.1.0/user-guide/developing-apps/index.html#streaming-k-means).
>
This application runs under DC/OS and has the following components that form stages of a pipeline:

1. **Data Ingestion:** The first stage reads data from a folder which is configurable and watchable. You can put new files in the folder and the file watcher will kickstart the data ingestion process. The first ingestion is however automatic and will be started 1 minute after the application installs.
2. **Data Transformation:** The second stage reads the data from the kafka topic populated in step 1, performs some transformations that will help in later stages of the data manipulation, and writes the transformed output into another Kafka topic. If there are any errors with specific records, these are recorded in a separate error Kafka topic. Stages 1 and 2 are implemented as a Kafka Streams application.
3. **Online Analytics and ML:** This stage of the pipeline reads data from the Kafka topic populated by stage 2, sets up a streaming context in Spark, and uses it to do streaming K-means clustering to detect network intrusion. A challenge is to determine the optimal value for K in a streaming context, i.e., by training the model, then testing with a different set of data. (More on this below.)
4. **An implementation of batch k-means:** Using this application, the user can iterate on the number of clusters (`k`) that should be used for the online anomaly detection part. The application accepts a batch duration and for all data that it receives in that duration it runs k-means clustering in batch for all values of `k` that fall within the range as specified by the user. The user can specify the starting and ending values of `k` and the increment step size as command line arguments and the application will run k-means for the entire range and report the cluster score (mean squared error). The optimal value of `k` can then be found using the elbow method.


## Data for the application

The application uses the dataset from [KDD Cup 1999](https://kdd.ics.uci.edu/databases/kddcup99/kddcup99.html), which asked competitors to develop a network intrusion detector. The reason for using this data set is that it makes a good case study for clustering and intrusion detection is a common use for streaming platforms like FDP.

## Installing the application

The easiest way to install the Network Intrusion Detection application is to install it from the pre-built docker image that comes with the Fast Data Platform distribution. Start from `fdp-package-sample-apps/README.md` of the distribution for general instructions on how to deploy the image as a Marathon application.

Once you have installed the docker image (we call it the laboratory) with the default name `fdp-apps-lab`, you can follow the steps outlined in that document to complete the installation of the application. The following part of this document discusses the installation part in more details.

> Assumption: We have `fdp-apps-lab` running in the FDP DC/OS cluster

```bash
$ pwd
<home directory>/fdp-package-sample-apps
$ cd bin/nwintrusion
$ ./app-install.sh
```

The default invocation of the script will install and run all the services of this application.

Try the `--help` option for `app-install.sh` for the command-line options.

The script `app-install.sh` takes all configuration parameters from a properties file.  The default file is `app-install.properties` which resides in the same directory, but you can specify the file with the `--config-file` argument.  It is recommended that you keep a set of configuration files for personal development, testing, and production.  Simply copy the default file over and modify as needed.

```
## dcos kafka package - valid values : confluent-kafka | kafka
kafka-dcos-package=kafka

## dcos service name. beta-kafka is installed as kafka by default. default is value of kafka-dcos-package
kafka-dcos-service-name=kafka

## whether to skip creation of kafka topics - valid values : true | false
skip-create-topics=false

## kafka topic partition
kafka-topic-partitions=2

## kafka topic replication factor
kafka-topic-replication-factor=2

## laboratory mesos deployment
laboratory-mesos-path=http://fdp-apps-lab.marathon.mesos
```

> The installation process fetches the data required from the canned docker image in `fdp-apps-lab`.

Once the installation is complete, the required services and Spark drivers should be seen available on the DC/OS console. One Marathon service will be up named `nwin-transform-data` and there will be 2 Spark drivers - one for Spark Clustering and the other for Batch K-Means.

> *Besides installing from the supplied docker image, the distribution also publishes the development environment and the associated installation scripts in `fdp-sample-apps/nwintrusion/bin` folder. The prerequisite of using these scripts is to have a developer version of the laboratory available as part of your cluster. This will be available in a future version of the platform.*

## Running the application

Once the required services are up, data ingestion starts within 1 minute. This is taken care of by a scheduler which schedules this ingestion process the first time. In case you need to do more ingestion, you can kickstart the pipeline by touching the data file already downloaded in the Mesos sandbox area. Do an `ssh` into the node that runs the `nwin-transform-data`, go to the Mesos sandbox area, the path of which can be found if you click on the running process from the Mesos Console (http://<mesos-master>/mesos). The folder named `data` will contain the data file. Simply do a `touch <filename>` (you may need to sudo for this) and the whole pipeline should start running.

Data ingested from data file -> Write to topic `nwin` -> `nwin-transform-data` does the transformation into topic `nwout` -> Anomaly detection Spark streaming process does online clustering into topic `nwcls`. Parallely on the data from topic `nwout`, a batch k-means process starts that tries to do a batch k-means clustering on the data.

## Removing the application

Just run `./app-remove.sh`.

It also has a `--help` option to show available command-line options. For example, use `--skip-delete-topics` if your cluster does not support deleting topics.

## Output of Running the App

### Batch K-Means

The idea behind batch k-means is to use it as a tool to fine tune the clustering process of anomaly detection. In other words, running `BatchKMeans` will give you an idea of what to pass as the value of `k` in the anomaly detection application (`SparkClustering`). Currently `BatchKMeans` iterates on the cluster number (`k`) range passed to it and prints the mean squared error for each of the values of `k` in the standard output. The optimal value an be detected using the elbow method.

### Visualization of Anomaly Detection

The anomaly detection application finds out probable intrusion records through a heuristics model. The current model identifies those points as probable intrusion which are at a distance that exceeds the 100th farthest point from the nearest centroid. Obviously this heuristics will not work on all data sets. The current application displays all such probable intrusions to a Grafana dashboard via an InfluxDB instance running in the cluster. The points are displayed based on the distance of the points from the centroid. These are only hints and the ones on the higher side of the plots have a higher probability of being the actual intrusion. This can be visualized better with appropriate alert configuration on the dashboard based on some threshold distance.

The distribution includes a configuration file for InfluxDB and Grafana named `influx.conf`:

```
visualize {
  influxdb {
    server = "http://influxdb.marathon.l4lb.thisdcos.directory"
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

It's assumed that InfluxDB is installed in the cluster from the Mesosphere catalog and Grafana is also installed from the docker image supplied along with the Fast Data Platform distribution.

Once the application is installed, the visualization of (possibly) anomalous intrusions can be seen by logging into the Grafana dashboard named *NetworkIntrusion*. There is a preset alert in the dashboard that can be customized to set up the proper threshold beyond which we classify points as anomalous.

## Access the Sample Apps from Zeppelin
Instead of deploying the sample apps using the procedure outlined above, you can also try them out in a notebook environment. FDP bundles a custom build of Apache Zeppelin that contains source code for these sample apps adapted to notebooks formats.

If you didn't already install Zeppelin, use the FDP installer command, `bin/fdp-start-base.sh --with-zeppelin`.

Then from under this project, run the following commands:
```bash
cd bin
./app-install.sh --use-zeppelin
```
Note the output of these commands. They will be useful later.

Then open Zeppelin UI from DC/OS. You should be able to see a folder "FDP Sample Apps" that contains two notebooks: SparkClustering and BatchKMeans. Open any one of them. If this is the first time you start Zeppelin, you will be prompted to save your interpreter settings. You can go with the default settings. Simply press the "Save" button. After you open a notebook, the first paragraph contains some information about the contents of the notebook. In the second paragraph, you will be asked to copy part of the output you obtained previously by running `app-install.sh` to the cell. Hit `Shift + Return` to run a paragraph when done. You are free to change the parameters in the notebook.

There are currently a few limitations with Zeppelin compared to running the sample apps by deploying them to DC/OS.
1. Only one Spark streaming context can be running at a time, meaning you cannot start streaming contexts in both SparkClustering and BatchKMeans notebooks.
2. Zeppelin does not support stopping a started streaming context that is still running. To test the other notebook after starting streaming context in one notebook, you need to restart Zeppelin service from the DC/OS UI.
