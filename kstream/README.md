# Kafka Streams Sample App for Weblog Processing

This project consists of 2 applications that demonstrate the use of Kafka Streams.  Both apps ingest sample web log data from the [Clarknet dataset](http://ita.ee.lbl.gov/html/contrib/ClarkNet-HTTP.html), which is described below.

> These two traces contain two week's worth of all HTTP requests to the ClarkNet WWW server. ClarkNet is a full Internet access provider for the Metro Baltimore-Washington DC area.

The sample application bundle uses the above dataset and has 2 separate main applications, both accessible through http interfaces:

* *WeblogProcessing* - The one based on Kafka Streams DSL APIs computes aggregate information from stateful streaming like the total number of bytes transferred for a specific host or the total number of accesses made on a specific host. These can be computed on a windowed aggregation as well. 
* *WeblogDriver* - The one based on Kafka Streams Procedure APIs implement a custom state store in Kafka Streams to check for membership information. It uses a bloom filter to implement the store on top of the APIs that KS provides. Then it consumes the Clarknet data and gives the user an http interface to check if the application has seen a specific host in its pipeline (membership query).

Together these samples demonstrate the following features of Kafka Streams:

1. Building and configuring a Streams based topology using Kafka Streams DSL as well as the lower level processor based APIs
2. Transformation semantics applied to streams data
3. Stateful transformation using *local* state stores
4. Interactive queries in Kafka streams applied to a distributed application
5. Implementing *custom* state stores
6. Interactive queries over custom state stores in a distributed setting

## Installing the Applications

The easiest way to install the Kafka Streams based sample applications is to install it from the pre-built docker image that comes with the Fast Data Platform distribution. Start from `fdp-package-sample-apps/README.md` of the distribution for general instructions on how to deploy the image as a Marathon application.

Once you have installed the docker image (we call it the *laboratory*) with the default name `fdp-apps-lab`, you can follow the steps outlined in that document to complete the installation of the application. The following part of this document discusses the installation part in more details.

> Assumption: We have `fdp-apps-lab` running in the FDP DC/OS cluster

```
$ pwd
<home directory>/fdp-package-sample-apps
$ cd bin/kstream
$ ./app-install.sh --help
  Installs the Kafka Streams sample application. Assumes DC/OS authentication was successful
  using the DC/OS CLI.

  Usage: app-install.sh   [options]

  eg: ./app-install.sh

  Options:
  --config-file        Configuration file used to launch applications
                       Default: ./app-install.properties
  --start-only X       Only start the following apps:
                         dsl         Starts topology based on Kafka Streams DSL
                         procedure   Starts topology that implements custom state repository based on Kafka Streams procedures
                       Repeat the option to run more than one.
                       Default: runs all of them
  -n | --no-exec       Do not actually run commands, just print them (for debugging).
  -h | --help          Prints this message.
```

Some of the valid options to install the applications are:

```bash
$ ./app-install.sh --start-only dsl
```

This will install the DSL based module as one of the applications running under Marathon in the DC/OS cluster.

```bash
$ ./app-install.sh --start-only procedure
```

This will install the lower level procedure based module as one of the applications running under Marathon in the DC/OS cluster.

```bash
$ ./app-install.sh
```

This will install both the modules as applications running under Marathon in the DC/OS cluster.

**If you decide to install and run both the applications together, please ensure your Kafka cluster is beefy enough to handle the load.**

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

Once the installation is complete, the required services should be seen available on the DC/OS console. One Marathon service will be up named `kstream-app-dsl` and the other one will be named `kstream-app-proc`.

> *Besides installing from the supplied docker image, the distribution also publishes the development environment and the associated installation scripts in `fdp-sample-apps/kstream/bin` folder. The prerequisite of using these scripts is to have a developer version of the laboratory available as part of your cluster. This will be available in a future version of the platform.*

### Starting the data ingestion process

Once the applications are installed as Marathon services, data ingestion will start within some time. Data is ingested from a folder named `data` within the Mesos Sandbox (`$MESOS_SANDBOX/data`). The application starts the first time ingestion automatically within 1 minute of the installation. *If you want to restart the ingestion process then you need to touch the data file within the folder*.

## Removing the Applications

The `bin` folder contains the script to remove the applications. This script works on the metadata files that the install script generates.

> Make sure that you run the remove script from the same folder you ran the install script

```bash
$ cd bin
$ ./app-remove.sh [--stop-only dsl] [--stop-only procedure] [--skip-delete-topics]
```

The above script cleans most of the data that the application generates including (optionally) the topics in Kafka. Still being a Kafka streams application, some of the state that it creates on the local filesystem has to be cleaned manually. Kafka has a utility that helps in this process. Please have a look in this [wiki page](https://cwiki.apache.org/confluence/display/KAFKA/Kafka+Streams+Application+Reset+Tool) for more details.

## Application REST APIs

Both examples run a self-contained http server that can be used to query information from the state stores used in the Kafka Streams pipelines. You can call these API's to determine if the sample applications are running correctly.

### DSL based module

This module demonstrates stateful streaming using local state stores. These state stores can be queried using interactive queries of Kafka streams through the http service developed as part of this application. The ingested data consists of http access logs of a number of URLs - this module supports 2 types of queries on the application state:

1. count of the number of accesses made to a specific host
2. count of the number of bytes transferred to a specific host

Here's how to query using http interface. We are using `curl` for demonstration here:

```bash
$ curl http://10.8.0.9:7070/weblog/access/world.std.com
```

This reports the number of accesses made to the host `world.std.com`.

```bash
$ curl http://10.8.0.9:7070/weblog/bytes/world.std.com
```

This reports the number of bytes transferred to the host `world.std.com`.

> **Note:** In the above `curl` command, the host IP specified has to be one that's accessible from outside the cluster. In case you are not within a VPN, you may need to use a public IP address. Also the ports need to be opened up in the AWS console (if you are running on AWS).

### Procedure based Custom State Store Module

This module demonstrates the use of custom state stores. This module has implemented a state store based on the Bloom Filter data structure. The purpose is to store membership information in a sublinear data structure. And this module offers interactive queries on top of this custom store. The query returns true or false depending on whether the data for the queried host has been stored or not.

Here's how to query using http interface. We are using `curl` for demonstration here:

```bash
$ curl http://10.8.0.9:7070/weblog/access/check/world.std.com
```

This reports `true` if the host `world.std.com` has been seen in the ingested data. Else it returns `false`.

> **Note:** In the above `curl` command, the host IP specified has to be one that's accessible from outside the cluster. In case you are not within a VPN, you may need to use a public IP address. Also the ports need to be opened up in the AWS console (if you are running on AWS).


> **Note:** When deployed on the cluster, Marathon assigns random free ports to both the applications. Check the log file (`kstream-dsl.log` or `kstream-proc.log`) for the assigned port number. The log files can be found from the Mesos console by clicking into the Mesos Task corresponding to the application. Go to `http://<Master URL>/mesos` and click on the appropriate task's Sandbox link. Move to the `logs` folder and look for the pattern **REST endpoint at http://0.0.0.0:25961** in the `kstream-*.log` file. In this example, `25961` is the assigned port number.

## Running in Distributed mode

Both the DSL based and Procedure based applications can be run in distributed mode. Multiple instances of the application can be run and all state can be queried using any of the host/port combination. Here are some things that need to be taken care of when running any of the applications in the distributed mode:

* The application `id` in the Marathon deployment json must be different for each of the instances. The typical way to ensure this is to use the given `app-install.sh` for installing one instance and then copying the deployment json and changing it for subsequent deployment instances.
* Some of the settings in the `cmd`, `env` and `uris` section of the json need to be different for subsequent deployment instances. Please refer to `kstream-app-dsl-subsequent-instances.json.template` or `kstream-app-proc-subsequent-instances.json.template` for the details of such changes.
* In order to run multiple instances, ensure that all Kafka topics are created with number of partitions > 1 (>= the number of instances run) with replication factors >= 1. Both these factors can be supplied in the `app-install.properties` file during installation.

## Application Restarts

Kafka streams manages state of the application for stateful streaming in local stores. For resiliency these stores are also backed up by Kafka topics, known as *changelog topics*. In case of application restarts, if it happens that the application is restarted ina different node of the cluster, Kafka will recreate the state store based on the compacted information of the changelog topic. This is also useful if the application faces any exception and Mesos does a restart albeit in a different node.

## Avro Serialization and Schema Registry

In order to demonstrate the capabilities of Schema Registry, the DSL module of the application serializes the weblog information using Avro into a Kafka topic named `avro-topic`. However, the schema registry interface has been developed as a pluggable module. In case the Schema Registry service is available, the application uses it for Avro serialization and schema management. In case Schema Registry is not installed, the application falls back to native Avro serialization.

## Interfacing with Confluent Connect

The DSL module of the application generates Avro data corresponding to the ingested records to a topic named `avro-topic`. We can set up Confluent Connect to consume from `avro-topic` and write to HDFS. Confluent repository offers out of the box HDFS Sink connectors that can do this. In this section we will discuss how to set this up in our DC/OS clustered environment.

### Prerequisites for the interface

In order for the interface to work, we need to ensure the following:

1. HDFS is installed in the DC/OS cluster and is up 'n running.
2. We have the custom lightbend universe running as a Marathon service.
3. Lightbend universe is set up as the default Universe (index = 0) in the DC/OS packaging.
4. Modifications for running Confluent Connect with HDFS are available in the installed Lightbend universe (check the `confluent-connect`package in the Universe).
5. Of course Confluent Connect has to run as a mandatory prerequisite.
6. Confluent schema registry service is up 'n running in the DC/OS cluster.

### Installing the Confluent Connect Distributed Worker

This has to be installed from the Lightbend Universe, which should be the default universe once we ensure Step 3 in the last section. Before installing the package, a bunch of internal topics need to be created manually. These topics are used by the Connect worker.

```bash
dcos beta-kafka topic create dcos-connect-configs --replication 3 --partitions 1 --name=kafka
dcos beta-kafka topic create dcos-connect-offsets --replication 3 --partitions 30 --name=kafka
dcos beta-kafka topic create dcos-connect-status --replication 3 --partitions 10 --name=kafka
```

Check that the topics have been created without error.

```bash
dcos beta-kafka topic list --name=kafka
```

The next step is to install the worker from the Universe. Here are some of the steps to install the worker from the DC/OS UI:

1. Select package `confluent-connect v1.0.0-3.2.2` from the Universe
2. Select Advanced Installation since we need to supply the hdfs URL
3. The installation page has a link named *hdfs* in the left pane. Click on the link and fill out *config-url* with the value `http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/endpoints`
4. Complete the installation

Or install from the DC/OS CLI:

Create a DC/OS service override json file `options.json`.  Note that `connect.zookeeper-connect` should be the ZK namespace of the Kafka cluster you wish to use.  `connect.kafka-service` should be the DC/OS service name of the Kafka DC/OS service you wish to use.

```json
{
  "connect": {
    "kafka-service": "kafka",
    "zookeeper-connect": "master.mesos:2181/dcos-service-kafka"
  },
  "hdfs": {
    "config-url": "http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/endpoints"
  }
}
```

Install `confluent-connect`:

```
dcos package install confluent-connect --package-version=1.0.0-3.2.2 --options=options.json
```

The `connect` package should now be available as a Marathon job in the DC/OS UI.

### Installing the HDFS Sink Connector

Once the worker and the internal topics are created, the next step is to create the actual connector. We will be installing the `HdfsSinkConnector` from Confluent which comes as an out of the box connector.

> `HdfsSinkConnector` needs to be configured with a topic from where it will consume Avro data. Please ensure this topic `avro-topic` is pre-installed before we configure the connector. The best way to ensure this is to install the Kafka Streams sample application beforehand which automatically installs all necessary topics.

The only way to install the connector is by using the REST APIs which Connect offers. Here's an example to install our `HdfsSinkConnector`:

```bash
curl -X POST -H "Content-Type: application/json" --data '{"name": "ks-hdfs-sink", "config": {"connector.class":"HdfsSinkConnector", "tasks.max":"1", "hdfs.url":"hdfs://hdfs", "topics":"avro-topic", "flush.size":"1000" }}' http://10.8.0.19:9622/connectors
```
Here the last URL `http://10.8.0.19:9622/connectors` refers to the host / port where the `connect` runs.

For more details on how to configure and manage connectors, have a look at this [Confluent Page](http://docs.confluent.io/current/connect/managing.html).

If the above setup steps went fine, then when you run the application for Kafka Streams DSL module, records will be generated in the `hdfs://hdfs/topics/avro-topic` directory.

Note: To view the contents of avro filese from the command line you can use `avrocat`, which comes with the avro distribution.  There's also a tool called [`fastavro`](https://github.com/tebeka/fastavro) which is more lightweight and easier to get going by installing the pip.

Example)

```bash
# Install fastavro package with pip
pip install fastavro
# Pluck a test file from HDFS
hdfs dfs -get /topics/avro-topic/partition=0/avro-topic+0+0000292000+0000292999.avro avro-topic+0+0000292000+0000292999.avro
# Pretty print the contents
fastavro avro-topic+0+0000292000+0000292999.avro --pretty
```

## A note about versioning

Don't put a `version := ...` setting in your sub-project because versioning is completely
controlled by [`sbt-dynver`](https://github.com/dwijnand/sbt-dynver) and enforced by the `Enforcer` plugin found in the `build-plugin`
directory.
