# Flink Sample Application

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

## Installing the application

The installation of the application is done using the laboratory process running under Marathon. The installation will deploy the generated artifacts to the laboratory and use them to run the application as a Marathon service.

> Please ensure an appropriate instance of the laboratory is running in the cluster. In the following we assume that the laboratory instance is named `fdp-apps-lab`.

Start by using the `bin` scripts. Using the default options and assuming the DC/OS CLI is on your local machine, run these commands:

```bash
$ cd bin
$ ./app-install.sh # Install and run the application components.

## install components separately
$ ./app-install.sh --start-only ingestion --start-only app
```

Try the `--help` option for `app-install.sh` for command-line options.

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

Once the installation is complete, both the Marathon applications  should be seen running on the DC/OS console. 

## Running the application

Once the required services are up, data ingestion starts within 1 minute. This is taken care of by a scheduler which schedules this ingestion process the first time. In case you need to do more ingestion, you can kickstart the pipeline by touching the data file already downloaded in the Mesos sandbox area. Do an `ssh` into the node that runs the `nyctaxi-load-data`, go to the Mesos sandbox area, the path of which can be found if you click on the running process from the Mesos Console (http://<mesos-master>/mesos) and touch the data file. Simply do a `touch <filename>` (you may need to sudo for this) and the whole pipeline should start running.

Data ingested from data file -> Write to topic `taxiin` -> `nyctaxi-app` does the travel time computation and writes into topic `taxiout`.

## Removing the application

Just run `./app-remove.sh`.

It also has a `--help` option to show available command-line options. For example, use `--skip-delete-topics` if your cluster does not support deleting topics.

## Output of running the application

The computation results for travel time prediction appears in the Kafka topic `taxiout`. You can run a consumer and check the predicted times as they flow across during processing of the application.

## A note about versioning

Don't put a `version := ...` setting in your sub-project because versioning is completely
controlled by [`sbt-dynver`](https://github.com/dwijnand/sbt-dynver) and enforced by the `Enforcer` plugin found in the `build-plugin`
directory.
