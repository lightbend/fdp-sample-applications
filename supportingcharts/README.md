# Installing prerequisites

Sample applications depends on the following components:
* Kafka - for communications
* Cassandra for final storage
* NFS for Spark checkpointing
* InfluxDB for real time data storage
* Grafana for viewing real time results
* Zeppelin for viewing of the content of Cassandra

Let’s describe installation on every component on Kubernetes/Openshft
   
## Kafka installation
If you want to use a separate kafka cluster for sample apps, follow these instructions, otherwise connect to the existing kafka cluster.

### Install Strimzi - Kafka operator

Installation is based on the following [documentation](https://github.com/lightbend/fdp-docs/blob/develop/src/main/paradox/management-guide/k8s/index.md). To install the operator (if not installed) run the following commands:

````
helm repo add strimzi http://strimzi.io/charts/
helm install strimzi/strimzi-kafka-operator --name strimzi-kafka --namespace lightbend
````
Due to the current limitation of Strimzi (watchNamespaces), actual Kafka cluster has to be created in the same project as the operator itself.

### Creating cluster

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

## Cassandra installation
     
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

## NFS installation
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

## InfluxDB installation
InfluxDB installation is based on the following [helm chart](../../supportingcharts/influxdbchart). It can be run using the following command:
````    
helm install kubernetes/charts/influxdbchart/
````    
After installation is complete, you should be able to see:
* influxdb deployment and a corresponding pod -  influxdb-xxxx, which is an InfluxDB server itself.
* influxdb service used to access influxDB

Programmatic access to influxDB (inside openshift cluster) by client in this case is through the service  `influxdb` using host `influxdb.sample.svc`  and port `8086`

## Grafana installation

Grafana installation is based on the following [helm chart](../../supportingcharts/grafanachart). The chart provides a lot of functionality (see Readme). I set values to the functionality appropriate for what we need in Openshift and did a couple of changes to deployment to create a simple login (admin/admin). It can be run using the following command:
````    
helm install kubernetes/charts/grafanachart/
````    
After installation is complete, you should be able to see:
* grafana deployment and a corresponding pod -  grafana-xxxx, which is a Grafana server.
* grafana service used to access Grafana
    
Programmatic access to Grafana (inside openshift cluster) by client in this case is through the service  `grafana` using host `grafana.sample.svc`  and port `80`.
Additionally, this service is exported (using route) to host http://grafana-sample.lightshift.lightbend.com/ making it available for end user consumption.

## Zeppelin installation

Zeppelin installation is based on the following [helm chart](../../supportingcharts/zeppelinchart). The chart provides a lot of functionality (see Readme). I set values to the functionality appropriate for what we need in Openshift and did a couple of changes to deployment (most importantly changed the image to Apache one, which is a later version and supports Cassandra, unlike the original one) It can be run using the following command:
````    
helm install kubernetes/charts/zeppelinchart/
````    
Once installed, the chart creates both Zeppelin pod and service. Unfortunately, although it is possible to create a route to the Zeppelin service, it does not work, returning a lot of 404s. Exposing Zeppelin pod using port-forward (port 8080), on another hand, works fine. In order for Zeppelin to work properly, it is necessary to configure Cassandra interpreter to point to the Cassandra running inside the cluster by setting host to - cassandra.sample.svc.
