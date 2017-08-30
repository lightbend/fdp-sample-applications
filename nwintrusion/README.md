# FDP sample application for network intrusion detection

> **NOTE:** For a more complete version of these instructions, see the [online instructions](https://developer.lightbend.com/docs/fast-data-platform/0.1.0/user-guide/developing-apps/index.html#streaming-k-means).
>
This application runs under DC/OS and has the following components that form stages of a pipeline:

1. **Data Ingestion:** The first stage reads data from an S3 bucket and writes to a Kafka topic, passed as a command line argument. 
2. **Data Transformation:** The second stage reads the data from the kafka topic populated in step 1, performs some transformations that will help in later stages of the data manipulation, and writes the transformed output into another Kafka topic. If there are any errors with specific records, these are recorded in a separate error Kafka topic. Stages 1 and 2 are implemented as a Kafka Streams application.
3. **Online Analytics and ML:** This stage of the pipeline reads data from the Kafka topic populated by stage 2, sets up a streaming context in Spark, and uses it to do streaming K-means clustering to detect network intrusion. A challenge is to determine the optimal value for K in a streaming context, i.e., by training the model, then testing with a different set of data. (More on this below.)
4. **An implementation of batch k-means:** Using this application, the user can iterate on the number of clusters (`k`) that should be used for the online anomaly detection part. The application accepts a batch duration and for all data that it receives in that duration it runs k-means clustering in batch for all values of `k` that fall within the range as specified by the user. The user can specify the starting and ending values of `k` and the increment step size as command line arguments and the application will run k-means for the entire range and report the cluster score (mean squared error). The optimal value of `k` can then be found using the elbow method.


## Data for the application

The application uses the dataset from [KDD Cup 1999](https://kdd.ics.uci.edu/databases/kddcup99/kddcup99.html), which asked competitors to develop a network intrusion detector. The reason for using this data set is that it makes a good case study for clustering and intrusion detection is a common use for streaming platforms like FDP.

## Installing the application

The installation of the application is done using the laboratory process running under Marathon. The installation will deploy the generated artifacts to the laboratory and use them to run the application as a Marathon service.

> Please ensure an appropriate instance of the laboratory is running in the cluster. In the following we assume that the laboratory instance is named `fdp-apps-lab`.

Start by using the `bin` scripts. Using the default options and assuming the DC/OS CLI is on your local machine, run these commands:

```bash
$ cd bin
$ ./app-install.sh # Install and run the application components.
```

Try the `--help` option for `app-install.sh` for command-line options.

The script `app-install.sh` takes all configuration parameters from a properties file.  The default file is `app-install.properties` which resides in the same directory, but you can specify the file with the `--config-file` argument.  It is recommended that you keep a set of configuration files for personal development, testing, and production.  Simply copy the default file over and modify as needed.

```
## dcos kafka package - valid values : kafka | confluent-kafka
kafka-dcos-package=confluent-kafka

## whether to skip creation of kafka topics - valid values : true | false
skip-create-topics=false

## kafka topic partition
kafka-topic-partitions=2

## kafka topic replication factor
kafka-topic-replication-factor=2

## name of the user used to publish the artifact.  Typically 'publisher'
publish-user="publisher"

## the IP address of the publish machine (where laboratory is running)
publish-host="fdp-apps-lab.marathon.mesos"

## port for the SSH connection. The default configuration is 9022
ssh-port=9022

## passphrase for your SSH key. Remove this entry if you don't need a passphrase
passphrase=

## the key file in ~/.ssh/ that is to be used to connect to the deployment host
ssh-keyfile="dg-test-fdp.pem"

## laboratory mesos deployment
laboratory-mesos-path=http://fdp-apps-lab.marathon.mesos
```

> The installation process fetches the data required from a pre-configured S3 bucket `fdp-sample-apps-artifacts`.

Once the installation is complete, the required services and Spark drrivers should be seen available on the DC/OS console. One Marathon service will be up named `nwin-transform-data` and there will be 2 Spark drivers - one for Spark Clustering and the other for Batch K-Means.

## Running the application

Once the required services are up, we need to kickstart the pipeline by touching the data file already downloaded in the Mesos sandbox area. Do an ssh into the node that runs the `nwin-transform-data`, go to the Mesos sandbox area, the path of which can be found if you click on the running process from the Mesos Console (http://<mesos-master>/mesos). The folder named `data` will contain the data file. Simply do a `touch <filename>` (you may need to sudo for this) and the whole pipeline should start running.

Data ingested from data file -> Write to topic `nwin` -> `nwin-transform-data` does the transformation into topic `nwout` -> Anomaly detection Spark streaming process does online clustering into topic `nwcls`. Parallely on the data from topic `nwout`, a batch k-means process starts that tries to do a batch k-means clustering on the data.

## Removing the application

Just run `./app-remove.sh`.

It also has a `--help` option to show available command-line options. For example, use `--skip-delete-topics` if your cluster does not support deleting topics.

## Output of Running the App

### Anomaly Detection

Anomaly detection application writes the output as a delimiter separated file in the Kafka topic `nwcls`. The format of the data written is as follows:

For every microbatch, the output starts with a record containing all the cluster centroids. It is of the following format and will only be present as the first record for a microbatch being processed:

`Centroids:/<cluster centroid 1>/<cluster centroid 2>/...`

Here <cluster centroid i> refers to a cluster centroid which is a vector of appropriate dimension. The cluster centroids are ordered based on the cluster number.

The record of cluster centroids is followed by each data point processed in the microbatch. Each of these records contain the following information, separated by comma (`,`):

1. Predicted cluster number
2. Nearest centroid of this data point
3. The distance to nearest centroid of this data point
4. Label
5. `true`, if this point is anomalous, `false`, otherwise
6. The data point itself, being a vector of appropriate dimension, as a comma delimited string

The format is as follows:

`<Predicted Cluster No>,<centroid>,<distance to centroid>,<label>,<if anomalous>,<vector>`.

Both the record of cluster centroids and the cluster details go to topic `nwcls`.

### Batch K-Means

The idea behind batch k-means is to use it as a tool to fine tune the clustering process of anomaly detection. In other words, running `BatchKMeans` will give you an idea of what to pass as the value of `k` in the anomaly detection application (`SparkClustering`). Currently `BatchKMeans` iterates on the cluster number (`k`) range passed to it and prints the mean squared error for each of the values of `k` in the standard output. The optimal value an be detected using the elbow method.

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
