# KillrWeather

KillrWeather is a reference application (which is adopted from Datastax's https://github.com/killrweather/killrweather) showing how to easily leverage and integrate [Apache Spark](http://spark.apache.org), [Apache Cassandra](http://cassandra.apache.org), [Apache Kafka](http://kafka.apache.org), [Akka](https://akka.io), and [InfluxDB](https://www.influxdata.com/) for fast, streaming computations. This application focuses on the use case of **[time series data](https://github.com/killrweather/killrweather/wiki/4.-Time-Series-Data-Model)**.

This application also can be viewed as a prototypical IoT (or sensors) data collection application, which stores data in the form of a time series.

> **Disclaimer:** This sample application is provided as-is, without warranty. It is intended to illustrate techniques for implementing various scenarios using Fast Data Platform, but it has not gone through a robust validation process, nor does it use all the techniques commonly employed for highly-resilient, production applications. Please use it with appropriate caution.

## Sample Use Case

_I need fast access to historical data on the fly for predictive modeling with real time data from the stream._

The application does not quite do that, it stops at capturing real-time and cumulative information.

## Reference Application

There are several versions this application:
* [KillrWeather App](https://github.com/killrweather/killrweather/tree/master/killrweather-app/src/main/scala/com/datastax/killrweather) is based on Spark Streaming.
* [KillrWeather App Structured](https://github.com/lightbend/fdp-killrweather/blob/master/killrweather-app_structured/src/main/scala/com/lightbend/killrweather/app/structured/KillrWeatherStructured.scala) is a version of the same, based on Spark Structured Streaming.
* [KillrWeather Beam](https://github.com/lightbend/fdp-killrweather/blob/master/killrweather-beam/src/main/scala/com/lightbend/killrweater/beam/KillrWeatherBeam.scala) experimental version of the same application based on [Apache Beam](https://beam.apache.org/).
This version only runs locally (using embedded Kafka). Cluster version is coming soon

## Time Series Data

The use of time series data for business analysis is not new. What is new is the ability to collect and analyze massive volumes of data in sequence at extremely high velocity to get the clearest picture to predict and forecast future market changes, user behavior, environmental conditions, resource consumption, health trends and much, much more.

Apache Cassandra is a NoSQL database platform particularly suited for these types of Big Data challenges. Cassandra’s data model is an excellent fit for handling data in sequence regardless of data type or size. When writing data to Cassandra, data is sorted and written sequentially to disk. When retrieving data by row key and then by range, you get a fast and efficient access pattern due to minimal disk seeks – time series data is an excellent fit for this type of pattern. Apache Cassandra allows businesses to identify meaningful characteristics in their time series data as fast as possible to make clear decisions about expected future outcomes.

There are many flavors of time series data. Some are windows into the stream, others cannot be windows, because the queries are not by time slice but by specific year, month, day, hour, etc. Spark Streaming lets you do both.

In addition to Cassandra, the application also demonstrates integration with InfluxDB and Grafana. This toolset is typically used for DevOps monitoring purposes, but it can also be very useful for real-time visualization of stream processing. Data is written to influxDB as it gets ingested in the application and Grafana is used to provide a real time view of temperature, pressure, and dewpoint values, as the reports come in. Additionally, the application provides results of data rollups - daily and monthly mean, low, and high values.

## Start Here

The original [KillrWeather Wiki](https://github.com/killrweather/killrweather/wiki) is still a great source of information.

## Using the Applications

We foresee 2 groups of users:

* Users who just want to see how application runs. We provide prebuilt Docker images.
* Users who want to use this project as a starting point for their own applications, so they'll want to build the code themselves.

## Building and configuring applications

The Docker images already exist, but if you want to build the applications yourself, perhaps because you've made changes, then use the provided SBT build here in the source distribution. It leverages the [SBT Docker plugin](https://github.com/marcuslonnberg/sbt-docker).

It supports several commands:

* `sbt docker` builds a docker image locally
* `sbt dockerPush` pushes an image to the dockerHub
* `sbt dockerBuildAndPush` builds image and pushes it to the dockerHub



## Deploying The applications to FDP
The following templates for deploying application to DC/OS are provided:

* KillrWeather App: `killrweather-app/src/main/resources/killrweatherAppDocker.json.template`
* KillrWeather App (Structured): `killrweather-app_structured/src/main/resources/killrweatherApp_structuredDocker.json.template`
* GRPC Clicent: `./killrweather-grpclient/src/main/resources/killrweatherGRPCClientDocker.json.template`
* HTTP Clicent: `./killrweather-grpclient/src/main/resources/killrweatherHTTPClientDocker.json.template`
* Data Loader: `killrweather-loader/src/main/resources/killrweatherloaderDocker.json.template`

From the git repo itself, then run the following script to generate the JSON files from the templates, using an appropriate value for `VERSION`, e.g., `1.2.0`:

```bash
./process-templates.sh VERSION
```

Now you can deploy these apps to Fast Data Platform, starting with the loader. Note that the loader is actually deployed as a _pod_:

```bash
dcos marathon pod add killrweather-loader/src/main/resources/killrweatherloaderDocker.json
```

The loader writes everything directly to Kafka. The two provided clients are alternative, which listen for connections and then write to Kafak. Here is how you would run them, although you don't need them if you're running the loader:

```bash
dcos marathon app add killrweather-grpclient/src/main/resources/killrweatherGRPCClientDocker.json
```

or

```bash
dcos marathon app add killrweather-grpclient/src/main/resources/killrweatherHTTPClientDocker.json
```

Finally, run one of the apps, either,

```bash
dcos marathon app add killrweather-app/src/main/resources/killrweatherAppDocker.json
```

or

```bash
dcos marathon app add killrweather-app/src/main/resources/killrweatherApp_structuredDocker.json
```

## See What's Going On...

Go to the Spark Web console for this job at http://killrweatherapp.marathon.mesos:4040/jobs/
or http://killrweatherappstructured.marathon.mesos:4040/jobs/ (if the structured streaming version is used)
to see the minibatch and other jobs that are executed as part of this Spark Streaming job.

Go to http://leader.mesos/mesos/#/frameworks and search for `killrweatherapp` or `killrweatherappstructured` to get more info about the corresponding executors.

## Loading data

The application can use three different loaders, which are local client applications that can communicate with the corresponding cluster services or clients to send weather reports.:

1. Direct Kafka loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngester` pushes data directly to the Kafka queue
that an application is listening on.
2. HTTP loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngesterRest` writes data to KillrWeather HTTP client.
3. GRPC loader `com.lightbend.killrweather.loader.kafka.KafkaDataIngesterGRPC` writes data to KillrWeather GRPC client.

Use the commands we saw previously for data loading to run these commands locally.

## Monitoring and Viewing Results

Monitoring is done using InfluxDB and Grafana.

For information about setting up Grafana and InfluxDB, see this [article](https://mesosphere.com/blog/monitoring-dcos-cadvisor-influxdb-grafana/).

To open the Grafana UI, click the `grafana` service in the DC/OS _Services_ panel, then click the instance link.
Now click the URL for the `ENDPOINTS`.

> **Note:** If you are in a DC/OS EE cluster, the link will open with `https`. If this fails to load, replace with `http`.

In the Grafana UI, load the definitions in `./killrweather-app/src/main/resource/grafana.json`. (Click the upper-left-hand side Grafana icon, then _Dashboards_, then _Import_.) This will create a dashboard called _KillrWeather Data Ingestion_.

Once set up and once data is flowing through the system, you can view activity in this dashboard.

Applications themselves currently implement setup. So this information is here just for reference.

To view execution results, a Zeppelin notebook is used, configured for [Cassandra in Zeppelin](https://zeppelin.apache.org/docs/0.7.2/interpreter/cassandra.html).

Unfortunately, the version of Zeppelin in the DC/OS Catalog is very old. Lightbend has built an up-to-date Docker image with Zeppelin 0.7.2, which you should use. See the section _Installing Zeppelin_ in `fdp-package-sample-apps-X.Y.Z/README.md` for details.

After installing Zeppelin, configure it for use with the Cassandra SQL interpreter (available already as a Zeppelin plugin in the package). The most important settings are these:

```
name	                   value
cassandra.cluster        cassandra
cassandra.hosts	         node.cassandra.l4lb.thisdcos.directory
cassandra.native.port	   9042
```

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

## Installing KillrWeather on Openshift

These are step by step instructions for installing Killrweather on Openshift.
Installation is done in a separate project - sample. First create, if does not exist, a project:
````
oc new-project sample
````
###Installing prerequisites
Killrweather depends on the following components:
* Kafka - for communications
* Cassandra for final storage
* NFS for Spark checkpointing
* InfluxDB for real time data storage
* Grafana for viewing real time results
* Zeppelin for viewing of the content of Cassandra

Let’s describe installation on every component
   
#### Kafka installation
If you want to use a separate kafka cluster for sample apps, follow these instructions, otherwise connect to the existing kafka cluster.
#####Install Strimzi - Kafka operator

Installation is based on the following [documentation](https://github.com/lightbend/fdp-docs/blob/develop/src/main/paradox/management-guide/k8s/index.md). To install the operator (if not installed) run the following commands:
````
helm repo add strimzi http://strimzi.io/charts/
helm install strimzi/strimzi-kafka-operator --name strimzi-kafka --namespace lightbend
````
Due to the current limitation of Strimzi (watchNamespaces), actual Kafka cluster has to be created in the same project as the operator itself.
##### Creating cluster
To create a cluster I was using the following cluster.yaml file, which creates a cluster with 3 brokers and 1 zookeeper
````
apiVersion: kafka.strimzi.io/v1alpha1
kind: Kafka
metadata:
  name: sample-cluster
spec:
  kafka:
    replicas: 3
    listeners:
      plain: {}
      tls: {}
    readinessProbe:
      initialDelaySeconds: 15
      timeoutSeconds: 5
    livenessProbe:
      initialDelaySeconds: 15
      timeoutSeconds: 5
    config:
      offsets.topic.replication.factor: 1
      transaction.state.log.replication.factor: 1
      transaction.state.log.min.isr: 1
    storage:
      type: ephemeral
    metrics: {}
  zookeeper:
    replicas: 1
    readinessProbe:
      initialDelaySeconds: 15
      timeoutSeconds: 5
    livenessProbe:
      initialDelaySeconds: 15
      timeoutSeconds: 5
    storage:
      type: ephemeral
    metrics: {}
  entityOperator:
    topicOperator: {}
    userOperator: {}
````
 
Which creates a cluster by using the following command
````
oc apply -f cluster.yaml -n lightbend
````
You can also get information about clusters by running the following command
````
oc get kafka -n lightbend
````
Programmatic access to cluster (inside openshift cluster) by client in this case is through the service `sample-cluster-kafka-brokers` using host `sample-cluster-kafka-brokers.lightbend.svc`  and port `9092`

#### Cassandra installation
     
Cassandra installation is based on the following [helm chart](../../supportingcharts/extendedcassandrachart). For more information on this chart refer to documentation here. Cassandra-reaper requires privilege user (yes its a security risk, but okay for sample application). This can be achieved using the following command 
````     
oc adm policy add-scc-to-user anyuid -nsample -z default
````
After this command is executed - install chart
````     
helm install kubernetes/charts/extendedcassandrachart/
````     
After the chart is installed you should see the following:
* cassandra stateful set and corresponding pods running cassandra itself
* cassandra-reaper deployment with corresponding pod running cassandra
* cassandra service providing access to Cassandra
     
Note: There still can be a problem with cassandra-reaper pod. Although most of the time reaper-db is created by a script, sometimes it does not. If a reaper fails to start properly go to cassandra-0 pod terminal and run following commands:````     
````
# cqlsh
Connected to sample at 127.0.0.1:9042.
[cqlsh 5.0.1 | Cassandra 3.11.1 | CQL spec 3.4.4 | Native protocol v4]
Use HELP for help.
cqlsh> CREATE KEYSPACE IF NOT EXISTS reaper_db WITH replication = {'class': 'NetworkTopologyStrategy', 'DC1': 1}; DESCRIBE keyspaces;
     
reaper_db  system_schema  system_auth  system  system_distributed  system_traces
     
cqlsh> exit;
#
````     
Programmatic access to cluster (inside openshift cluster) by client in this case is through the service  `cassandra using` host `cassandra.sample.svc`  and port `9042`
     
After experimenting with this helm chart, it turns out to be unstable on Openshift (stable on GKE), so I switched to a simpler [helm chart](../../supportingcharts/cassandrachart). This one starts Cassandra without reaper and works out of the box.

####NFS installation
NFS installation is based on the following [helm chart](../../supportingcharts/nfschart). This chart requires privilege user (yes its a security risk, but okay for sample application). This can be achieved using the following command 
````     
oc adm policy add-scc-to-user privileged -nsample -z default
````     
After this command is executed - install chart
````     
helm install kubernetes/charts/nfschart/
````     
After the chart is executed, you should see the following resources in the sample project:
* nfs-server replication controller (deployment) and a corresponding pod -  nfs-server-xxxx, which is an NFS server itself
* nfs-persistent-volume - persistent volume mapped to the NFS server
* nfs-persistent-volume-claim - persistent volume claim mapped to the above volume and used for Spark checkpointing. 
Openshift out of the box does not support dynamic NFS provisioner, so we are using static provisioning here.

####InfluxDB installation
InfluxDB installation is based on the following [helm chart](../../supportingcharts/influxdbchart). It can be run using the following command:
````    
helm install kubernetes/charts/influxdbchart/
````    
After installation is complete, you should be able to see:
* influxdb deployment and a corresponding pod -  influxdb-xxxx, which is an InfluxDB server itself.
* influxdb service used to access influxDB

Programmatic access to influxDB (inside openshift cluster) by client in this case is through the service  `influxdb` using host `influxdb.sample.svc`  and port `8086`
#### Grafana installation
Grafana installation is based on the following [helm chart](../../supportingcharts/grafanachart). The chart provides a lot of functionality (see Readme). I set values to the functionality appropriate for what we need in Openshift and did a couple of changes to deployment to create a simple login (admin/admin). It can be run using the following command:
````    
helm install kubernetes/charts/grafanachart/
````    
After installation is complete, you should be able to see:
* grafana deployment and a corresponding pod -  grafana-xxxx, which is a Grafana server.
* grafana service used to access Grafana
    
Programmatic access to Grafana (inside openshift cluster) by client in this case is through the service  `grafana` using host `grafana.sample.svc`  and port `80`.
Additionally, this service is exported (using route) to host http://grafana-sample.lightshift.lightbend.com/ making it available for end user consumption.
#### Zeppelin installation
Zeppelin installation is based on the following [helm chart](../../supportingcharts/zeppelinchart). The chart provides a lot of functionality (see Readme). I set values to the functionality appropriate for what we need in Openshift and did a couple of changes to deployment (most importantly changed the image to Apache one, which is a later version and supports Cassandra, unlike the original one) It can be run using the following command:
````    
helm install kubernetes/charts/zeppelinchart/
````    
Once installed, the chart creates both Zeppelin pod and service. Unfortunately, although it is possible to create a route to the Zeppelin service, it does not work, returning a lot of 404s. Exposing Zeppelin pod using port-forward (port 8080), on another hand, works fine. In order for Zeppelin to work properly, it is necessary to configure Cassandra interpreter to point to the Cassandra running inside the cluster by setting host to - cassandra.sample.svc.

### Installing Killrweather
In order for original [helm chart](helm-pvc) has to be extended with the role and role binding definition:
````   
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
````   
Which grants containers the ability to watch for pods.
After this was added, the chart deploys killrweather application.  
   
*Note*: One of the things that make Openshift “special” is a more rigorous requirement to placing ‘ around content of --conf content. Without ‘ execution of config is unpredictable.
