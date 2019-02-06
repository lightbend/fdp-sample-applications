# Installing prerequisites

Some of the sample applications depend on one or more of the following components:

* Kafka for communications between services
* Cassandra for final storage
* NFS for Spark checkpointing
* InfluxDB for real time data storage
* Grafana for viewing real time results
* Zeppelin for viewing the contents of Cassandra tables

Here are the installation instructions for OpenShift and Kubernetes.

> **Note:** In what follows (except where noted), we'll assume OpenShift and use the `oc` command. For Kubernetes, substitute the `kubectl` command instead.

## Kafka Installation

If you want to use a separate Kafka cluster for the sample apps, follow these instructions. Otherwise, connect to an existing Kafka cluster. You'll need to edit configuration files for the sample apps you deploy so they use your target cluster.

### Install Strimzi - The Kafka Operator for Kubernetes and OpenShift

See the Fast Data Platform [installation instructions for Strimzi](https://developer.lightbend.com/docs/fast-data-platform/current/#strimzi-operator-kafka).

Due to a current limitation of Strimzi (`watchNamespaces`), the actual Kafka cluster has to be created in the same project as the operator itself.

### Creating a Kafka Cluster

To create a Kafka cluster, edit the following `cluster.yaml` file to taste. As written, it creates a cluster with three Kafka brokers and one ZooKeeper node:

```yaml
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
```

Use this file with the following command to create the Kafka cluster:

```bash
oc apply -f cluster.yaml -n lightbend
```

You can also get information about any installed clusters under the `lightbend` namespace with the following command:

```bash
oc get kafka -n lightbend
```

Programmatic access to the Kafka cluster inside the OpenShift or Kubernetes cluster is through the service `sample-cluster-kafka-brokers` using host `sample-cluster-kafka-brokers.lightbend.svc`  and port `9092`. If you aren't using the `lightbend` namespace, change the host accordingly.

## Cassandra Installation

There are two Cassandra installations provided. Through experimentation, we've found that the [`extendedcassandrachart` helm chart](./extendedcassandrachart) works well on GKE (Google Kubernetes Environment) and perhaps other Kubernetes installations (untested), while the [`cassandrachart` helm chart](./cassandrachart) works best on OpenShift.

### Using the Extended Cassandra Chart (GKE)

This chart uses a `cassandra-reaper` that requires privileged user access. (Yes, it's a security risk, but okay for a sample application). This can be achieved using the following command:

```bash
kubectl adm policy add-scc-to-user anyuid -nsample -z default
```

Now, install this chart with `helm`, where we assume you working in the same directory as this README file, i.e., `supportingcharts`:

```bash
helm install extendedcassandrachart
```

After the chart is installed you should see the following:

* `cassandra` stateful set and corresponding pods running Cassandra itself
* `cassandra-reaper` deployment with the corresponding pod running Cassandra
* `cassandra` service providing access to Cassandra

Note that sometimes the `cassandra-reaper` pod fails to start correctly; sometimes the `reaper-db` is not successfully created by an embedded script. When this happens, open the `cassandra-0` pod's terminal and run following commands:

```bash
# cqlsh
Connected to sample at 127.0.0.1:9042.
[cqlsh 5.0.1 | Cassandra 3.11.1 | CQL spec 3.4.4 | Native protocol v4]
Use HELP for help.
cqlsh> CREATE KEYSPACE IF NOT EXISTS reaper_db WITH replication = {'class': 'NetworkTopologyStrategy', 'DC1': 1}; DESCRIBE keyspaces;

reaper_db  system_schema  system_auth  system  system_distributed  system_traces

cqlsh> exit;
```

Programmatic access to Cassandra is through the service `cassandra` using host `cassandra.sample.svc` and port `9042`. If you used a different namespace than `sample`, change the host accordingly.

### Using the Cassandra Chart (OpenShift)

The simpler [`cassandrachart` helm chart](./cassandrachart) starts Cassandra without the reaper and works better on OpenShift.

```bash
helm install cassandrachart
```

Programmatic access to Cassandra is through the service `cassandra` using host `cassandra.sample.svc` and port `9042`.

## NFS Installation

NFS installation uses the [`nfschart` helm chart](./nfschart). This chart requires privileged user access. (Yes, its a security risk, but okay for sample applications). This can be achieved using the following command:

> **Note:** While this is a simple way to provide distributed file system access, Lightbend recommends GlusterFS for production scenarios. See the [storage discussion](https://developer.lightbend.com/docs/fast-data-platform/current/#_conclusion_storage_recommendations_for_fast_data_platform) in the Fast Data Platform docs for more information.

```bash
oc adm policy add-scc-to-user privileged -nsample -z default
```

Now install the chart:

```bash
helm install nfschart
```

You should see the following resources in the sample project:

* `nfs-server` - replication controller (deployment) and a corresponding pod, `nfs-server-xxxx`, which is an NFS server itself
* `nfs-persistent-volume` - persistent volume mapped to the NFS server
* `nfs-persistent-volume-claim` - persistent volume claim mapped to the above volume and used for Spark checkpointing.

OpenShift out of the box does not support dynamic NFS provisioning, so we are using static provisioning here.

## InfluxDB Installation

InfluxDB installation uses the [`influxdbchart` helm chart](./influxdbchart).

```bash
helm install influxdbchart
```

You should see the following:

* `influxdb` deployment and a corresponding pod, `influxdb-xxxx`, which contains the InfluxDB server itself
* `influxdb` service used to access influxDB

Programmatic access is through the service `influxdb` using host `influxdb.sample.svc` and port `8086`.

## Grafana Installation

Grafana installation uses the [`grafanachart` helm chart](./grafanachart). The chart provides a lot of functionality (see [its README](./grafanachart/README.md)). We used configuration values that are appropriate for what we need in our sample apps. For example, created a simple login: `admin`/`admin`.

```bash
helm install grafanachart
```

You should see the following:

* `grafana` deployment and a corresponding pod, `grafana-xxxx`, which is a Grafana server
* `grafana` service used to access Grafana

Programmatic access is through the service `grafana` using host `grafana.sample.svc` and port `80`. Additionally, this service is exported (using routes) to URL http://grafana-sample.lightshift.lightbend.com/ so it is available for convenient access.

## Zeppelin Installation

Zeppelin installation uses the [`zeppelinchart` helm chart](./zeppelinchart). The chart provides a lot of functionality (see [its README](./zeppelinchart/README)). We used configuration values that are appropriate for what we need in our sample apps and we did a couple of changes to the deployment in the original version of this chart, such as using the Apache Docker image, which uses a later version of Zeppelin and supports Cassandra, unlike the original one.

```bash
helm install zeppelinchart
```

Once installed, the chart creates both a Zeppelin pod and service. Unfortunately, although it is possible to create a route to the Zeppelin service, it does not work, returning a lot of 404s. However, exposing the Zeppelin pod using port-forwarding (e.g., port 8080) works fine.

In order for Zeppelin to successfully display the data in the Cassandra tables, it is necessary to configure the Cassandra interpreter to point to the Cassandra instance running inside the cluster by setting host value to `cassandra.sample.svc`.
