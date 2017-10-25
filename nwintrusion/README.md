# FDP sample application for network intrusion detection

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

The easiest way to install the Network Intrusion Detection application is to install it from the pre-built docker image that comes with the Fast Data Platform distribution. Start [here](https://github.com/typesafehub/fdp-package-sample-apps/blob/develop/README.md) for general instructions on how to deploy the image as a Marathon application.

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

The distribution includes a configuration file for InfluxDB named `influx.conf`:

```
influxdb {
  server = "http://influx-db.marathon.l4lb.thisdcos.directory"
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
```

The InfluxDB instance in the cluster is pre-created with a database named `anomaly` and a retention policy named `default`. For details on how to install the custom InfluxDB instance in FDP cluster, please refer to the [document](https://github.com/typesafehub/fdp-influxdb-docker-images/blob/develop/README.md) on the InfluxDB project repository.

#### Setting up a Grafana Dashboard

Visualization is done through Grafana, which can be installed from the community package on the Mesosphere Universe. Once you install Grafana, the default login credentials are `user = admin, password = admin`. The host and port for Grafana instance can be located from the DC/OS console.

While installing Grafana, please use the following json as it contains some useful configuration for setting up of VIPs.

```json
{
  "id": "/grafana",
  "backoffFactor": 1.15,
  "backoffSeconds": 1,
  "container": {
    "portMappings": [
      {
        "containerPort": 3000,
        "hostPort": 0,
        "labels": {
          "VIP_0": "/grafana:3000"
        },
        "protocol": "tcp",
        "servicePort": 10009,
        "name": "grafana-api"
      }
    ],
    "type": "DOCKER",
    "volumes": [],
    "docker": {
      "image": "grafana/grafana:4.5.2",
      "forcePullImage": false,
      "privileged": false,
      "parameters": []
    }
  },
  "cpus": 0.3,
  "disk": 0,
  "env": {
    "GF_SECURITY_ADMIN_PASSWORD": "admin"
  },
  "healthChecks": [
    {
      "gracePeriodSeconds": 300,
      "ignoreHttp1xx": false,
      "intervalSeconds": 60,
      "maxConsecutiveFailures": 3,
      "portIndex": 0,
      "timeoutSeconds": 20,
      "delaySeconds": 15,
      "protocol": "HTTP",
      "path": "/api/health"
    }
  ],
  "instances": 1,
  "labels": {
    "DCOS_PACKAGE_OPTIONS": "eyJzZXJ2aWNlIjp7Im5hbWUiOiJncmFmYW5hIn0sImdyYWZhbmEiOnsiY3B1cyI6MC4zLCJtZW0iOjUxMiwiYWRtaW5fcGFzc3dvcmQiOiJhZG1pbiJ9LCJuZXR3b3JraW5nIjp7ImV4dGVybmFsX2FjY2VzcyI6eyJlbmFibGUiOmZhbHNlLCJleHRlcm5hbF9hY2Nlc3NfcG9ydCI6MCwidmlydHVhbF9ob3N0IjoiZ3JhZmFuYS5leGFtcGxlLm9yZyJ9fX0=",
    "DCOS_PACKAGE_SOURCE": "https://universe.mesosphere.com/repo",
    "DCOS_PACKAGE_METADATA": "eyJwYWNrYWdpbmdWZXJzaW9uIjoiMy4wIiwibmFtZSI6ImdyYWZhbmEiLCJ2ZXJzaW9uIjoiNC41LjItMC4zIiwibWFpbnRhaW5lciI6Imh0dHBzOi8vZGNvcy5pby9jb21tdW5pdHkiLCJkZXNjcmlwdGlvbiI6IkdyYWZhbmEgaXMgYSBsZWFkaW5nIG9wZW4gc291cmNlIGFwcGxpY2F0aW9uIGZvciB2aXN1YWxpemluZyBsYXJnZS1zY2FsZSBtZWFzdXJlbWVudCBkYXRhLiBJdCBwcm92aWRlcyBhIHBvd2VyZnVsIGFuZCBlbGVnYW50IHdheSB0byBjcmVhdGUsIHNoYXJlLCBhbmQgZXhwbG9yZSBkYXRhIGFuZCBkYXNoYm9hcmRzIGZyb20geW91ciBkaXNwYXJhdGUgbWV0cmljIGRhdGFiYXNlcywgZWl0aGVyIHdpdGggeW91ciB0ZWFtIG9yIHRoZSB3b3JsZC4gR3JhZmFuYSBpcyBtb3N0IGNvbW1vbmx5IHVzZWQgZm9yIEludGVybmV0IGluZnJhc3RydWN0dXJlIGFuZCBhcHBsaWNhdGlvbiBhbmFseXRpY3MsIGJ1dCBtYW55IHVzZSBpdCBpbiBvdGhlciBkb21haW5zIGluY2x1ZGluZyBpbmR1c3RyaWFsIHNlbnNvcnMsIGhvbWUgYXV0b21hdGlvbiwgd2VhdGhlciwgYW5kIHByb2Nlc3MgY29udHJvbC4gR3JhZmFuYSBmZWF0dXJlcyBwbHVnZ2FibGUgcGFuZWxzIGFuZCBkYXRhIHNvdXJjZXMgYWxsb3dpbmcgZWFzeSBleHRlbnNpYmlsaXR5LiBUaGVyZSBpcyBjdXJyZW50bHkgcmljaCBzdXBwb3J0IGZvciBHcmFwaGl0ZSwgSW5mbHV4REIgYW5kIE9wZW5UU0RCLiBUaGVyZSBpcyBhbHNvIGV4cGVyaW1lbnRhbCBzdXBwb3J0IGZvciBLYWlyb3NEQiwgYW5kIFNRTCBpcyBvbiB0aGUgcm9hZG1hcC4gR3JhZmFuYSBoYXMgYSB2YXJpZXR5IG9mIHBhbmVscywgaW5jbHVkaW5nIGEgZnVsbHkgZmVhdHVyZWQgZ3JhcGggcGFuZWwgd2l0aCByaWNoIHZpc3VhbGl6YXRpb24gb3B0aW9ucy5cblxuVGhpcyBwYWNrYWdlIGNhbiBiZSB1c2VkIGFsb25nc2lkZSB0aGUgREMvT1MgJ2NhZHZpc29yJyBhbmQgJ2luZmx1eGRiJyBwYWNrYWdlcyBmb3IgYSBjbHVzdGVyLXdpZGUgbW9uaXRvcmluZyBzb2x1dGlvbi5cblxuSW5zdGFsbGF0aW9uIERvY3VtZW50YXRpb246IGh0dHBzOi8vZ2l0aHViLmNvbS9kY29zL2V4YW1wbGVzL3RyZWUvbWFzdGVyL2NhZHZpc29yLWluZmx1eGRiLWdyYWZhbmFcblxuIiwidGFncyI6WyJncmFmYW5hIiwibW9uaXRvcmluZyIsInZpc3VhbGl6YXRpb24iXSwic2VsZWN0ZWQiOmZhbHNlLCJzY20iOiJodHRwczovL2dpdGh1Yi5jb20vZ3JhZmFuYS9ncmFmYW5hIiwid2Vic2l0ZSI6Imh0dHBzOi8vZ3JhZmFuYS5uZXQvIiwiZnJhbWV3b3JrIjpmYWxzZSwicHJlSW5zdGFsbE5vdGVzIjoiVGhpcyBEQy9PUyBTZXJ2aWNlIGlzIGN1cnJlbnRseSBpbiBwcmV2aWV3LiBUaGVyZSBtYXkgYmUgYnVncywgaW5jb21wbGV0ZSBmZWF0dXJlcywgaW5jb3JyZWN0IGRvY3VtZW50YXRpb24sIG9yIG90aGVyIGRpc2NyZXBhbmNpZXMuXG5cbmBgYEFkdmFuY2VkIEluc3RhbGxhdGlvbiBvcHRpb25zIG5vdGVzYGBgXG5cbm5ldHdvcmtpbmcgLyAqZXh0ZXJuYWxfYWNjZXNzKjogY3JlYXRlIGFuIGVudHJ5IGluIE1hcmF0aG9uLUxCIGZvciBhY2Nlc3NpbmcgdGhlIHNlcnZpY2UgZnJvbSBvdXRzaWRlIG9mIHRoZSBjbHVzdGVyXG5cbm5ldHdvcmtpbmcgLyAqZXh0ZXJuYWxfYWNjZXNzX3BvcnQqOiBwb3J0IHRvIGJlIHVzZWQgaW4gTWFyYXRob24tTEIgZm9yIGFjY2Vzc2luZyB0aGUgc2VydmljZS4iLCJwb3N0SW5zdGFsbE5vdGVzIjoiU2VydmljZSBpbnN0YWxsZWQuXG5cbkl0IGlzIHJlY29tbWVuZGVkIHRvIGFjY2VzcyB0aGlzIHNlcnZpY2UgdGhyb3VnaCB0aGUgZW5kcG9pbnQgY3JlYXRlZCBpbiBNYXJhdGhvbi1MQi5cblxuRGVmYXVsdCBsb2dpbjogYGFkbWluYC9gYWRtaW5gLiIsImxpY2Vuc2VzIjpbeyJuYW1lIjoiQXBhY2hlIExpY2Vuc2UiLCJ1cmwiOiJodHRwOi8vd3d3LmFwYWNoZS5vcmcvbGljZW5zZXMvTElDRU5TRS0yLjAifV0sImltYWdlcyI6eyJpY29uLXNtYWxsIjoiaHR0cHM6Ly9zMy5hbWF6b25hd3MuY29tL2Rvd25sb2Fkcy5tZXNvc3BoZXJlLmlvL3VuaXZlcnNlL2Fzc2V0cy9pY29uLXNlcnZpY2UtZ3JhZmFuYS1zbWFsbC5wbmciLCJpY29uLW1lZGl1bSI6Imh0dHBzOi8vczMuYW1hem9uYXdzLmNvbS9kb3dubG9hZHMubWVzb3NwaGVyZS5pby91bml2ZXJzZS9hc3NldHMvaWNvbi1zZXJ2aWNlLWdyYWZhbmEtbWVkaXVtLnBuZyIsImljb24tbGFyZ2UiOiJodHRwczovL3MzLmFtYXpvbmF3cy5jb20vZG93bmxvYWRzLm1lc29zcGhlcmUuaW8vdW5pdmVyc2UvYXNzZXRzL2ljb24tc2VydmljZS1ncmFmYW5hLWxhcmdlLnBuZyIsInNjcmVlbnNob3RzIjpbImh0dHA6Ly9ncmFmYW5hLm9yZy9hc3NldHMvaW1nL2Jsb2cvdjMuMC93UC1TY3JlZW5zaG90LWRhc2gtd2ViLnBuZyIsImh0dHBzOi8vcHJvbWV0aGV1cy5pby9hc3NldHMvZ3JhZmFuYV9wcm9tZXRoZXVzLWNiYjk0M2YwYmIzLnBuZyIsImh0dHBzOi8vZ3JhZmFuYS5jb20vYmxvZy9pbWcvZG9jcy92NDUvcXVlcnlfaW5zcGVjdG9yLnBuZyJdfX0=",
    "DCOS_SERVICE_NAME": "grafana",
    "DCOS_PACKAGE_DEFINITION": "eyJtZXRhZGF0YSI6eyJDb250ZW50LVR5cGUiOiJhcHBsaWNhdGlvbi92bmQuZGNvcy51bml2ZXJzZS5wYWNrYWdlK2pzb247Y2hhcnNldD11dGYtODt2ZXJzaW9uPXYzIiwiQ29udGVudC1FbmNvZGluZyI6Imd6aXAifSwiZGF0YSI6Ikg0c0lBQUFBQUFBQUFMVlk2Mi9pdUJiL1Z5enVsVVphbGZkd1IxUzZIMG9wTFV6THRGRENZM2MxZFJKRFhCdzdheWVrZE5ULy9SN2JDYVRBUEs1Vyt3R1JPTWZuL003VFB1ZGJLY0xlR3E4b1h6bEVLaXA0NmJ6VXJOUktaeVdPUXdJdks0bVhtR05ZMk93SVBsWmFsVWE1Vm1uQ3FpU01ZRVYydXh0bnBSQlRIc09QU0tBTjRqaFM1OVdxN3dsVm9hTHFpVEJNT0kyM3NOY255cE0waWkzWGF5c0pVWVV3QXFZK2dFSWlJaHdwa1VpUElCeEZqSHBZazZPbGtHaERWWUlaZmRWMERNc1ZLU3NQTTRKQ0FKUklFaEllSXgvSHVJTDZNWXFrMkZBUUNMd2prUks1VEJqQzNFY0FmNFdCTU1WYkZBdmtTWUpqY29aVWdDWDhHWXFYaUFsSkRDdXo0R01WdUFKTFg2R2xGQ0hhQWp6a1V4VmhDWHRCZkN5cFo4aGRzSXc2UTRUR0FaRW9oVDlMSEJNY0l0QUFsbEVxSlBNcnFLQjlLRlNNdEowRVoxdVVLT0liZGZzOEpwS1RHRkcrbEZqRk12RmlVTk5BS3BvR3VMQnRURDBRN0NZeENqRTNUQkRWTzVFd1VIeWhuYVJnd1dPSk1UVGxmZ0k4S1daSUVhNkVoTzJCQ0lGOUVnT3g1bnlHVWpBT2JMZDJBWXQ2UkNsQXltTXAyRjZGSlZBQk1JVWlscXhXMkFXWFJKZ1RwakxyZ1JtdFIyR0JNWkZxOGVDeUxWZzZCdEhVcFF6aW80SWVRUlRSQnZFU0tjR1pZQXd3YklCVUVrVkN4c1lvSURNS3FQWllueTlaOHRMdEdDRmZJR3dleDkxT2dRbG1TbWhmRWtsMVpHZzlDM3crWXlxRjZuYXNhdU9IVzcwRmpLazlKQVgyUXh6dEZReXdEcU1ObHBURVd5U1dtWHBuQlhPQ0ZSSUdnRE5iK0dpbGdWcENHd2hHbFR5RXJlZUVTUVZWK1lQL3dSOERBR0NUa3lBUGMrUVNHd3FZQ2I1U0VNb0dXL2V5K21XTVBuallCMVpDZmpEd1AxQmpDOS85a0hOUVJrbU1BSjZDS0NxbmVqL0VGNDJGMUhDVllJbVdiVVQzdVFMek1JdXBLN3pFMkV1L25hTThuVmVnUXVKV0lFcE5abGZKQ3c0alJsUTFsb1JVUTZ5bFZITlU1UnhQT2FzbVdncmtmNHhYcW5UK2U2SEc3Q0hwZ2xNMFR1blBzNUtDWlBWaTRwZk9PUmdYM3Iyd1VHRUtrREtHMVQzamxMZ0t3cVJJYnI5VklLT3FRQUFwRlJMSXhYWE9QSklrTThSUXhBUndsb3hMck1ISFJHNm9keENja0Yyd2FVTkptb2RkQ0VVRi9PWW1LeHNjUXRzSVNrU2VJWFlSOW50UXFZcUdQdFBWSVV0VkNrV1NRT0I0bE5qUWVIcDZ1dkEzc0FEUjhNNVhXZndncmdFRGxTWUc5YlJXMnNsVjlKdk9NQW4xNFN2MmRPNytkcDRWUEFnYkJMS2xVZUpPMTdGQThQSnR4NGFOb2RVY2RNU3BUSFZUK2tRU20xQ0VITkRmc3ZENnFkeXZPdTlBdUVrL3FMcDViUCtxOEFvNExJSXFlZUNnblZmc012RnRPTWZhVFdCanFLaUUreUFHSkZxMndCTSs1UnJGZ1JUSktqQ0NnREFTY0l4bDlqbUVaaGgzeVJJbkxFWk13UGw1anA2Z1NsRCtWTTMrYzR3VFR0K2h0TkVGMVJwS25YNy8vVnQrMUY1QXNvTG9XL3NKdGllU1pRRUw4WnFtYVFVYmlvcVFxMnJPb0hyYnY3d2FqcS9LRFRpMTN5QkpRSFlYTW5KMGNDNWJzV0dtUXVuOFcyblR1SWlpTzNDWTV2bElJRFN4U1JESWdsWC84bUxWRDltNi93elBQUDdrTlp5dEg3TG54YmlWektkMXRwelY2TzFGVGpkTS9ka2QvVUk3bjBqRDM4Nm5pOEFOcjVKNXMxUDNtdlgybnE3TzNISC9QLzJiK05PaU9RZ1dZUytaajF2eFlscHZMOGZwWjBOek9ZaGNQcXpOcDYzbmhlRjU4YkxmNzBCdDFmdFZSbXQrZEZSYkxDYzFwK3M0ZzRIVFk4dUh5V2c0bnJTV2s2dmU0NlR1MzAvV0Q1b1BKYzJZZTJFdkJON0JiZGhidTFPV3pKcWR3R3NPbTI1enNINm5rOFZ5TTJMdHdTTWJUaDRtTDNlemV1Zk9tZmp3Zm5mTWozZVVQL1UxZHRDbGIvSGQxTlFsdFJqbmpYYmlYL2NpTjNTMjJxWTdHMXM1TlRMck1HUHI5ZWorb1JiM0pyU2Y3ODEwSDIzbWpaaDV4L3Judm9yblU1L3QvQlU2cjI3VDJjNGJrMlErRzc0dVpxUFgyM0M0Y2Zrb3dOTVc4R210M2NaUUxtYjlaTytMZXJZMjBEcThsMjl4aG1DbjU0WFRxYnZYTHdOMzJ1TUw3YzlyOFBmTmtMMjNuZFViOUszNWpmWVdiNEh1Y25BNVdiT3JVVzFDVDlGNjErMnRmMVVQdkpzTzJNbC8xYnBNMDBPNnpxZGpYTWMybm1oZWx5Q3plNWZlZFM5TzZHTDM0T3YycTkvcmJEeHVZaVE5Z2N2OFNGTTk3M1Jwc0dTeGJURUMwZUdGcmNEdHRZTjVZOGk4NWwyeWdGaVloeTg2UDQ1OVpHMEl2aGcwOEhUSWRoZ2hIMzZKOXl5b3dWN0l2NWZsZkRxRS9CaStRdndDOWxFaGZ3NSt3UHUyMFdMK2piL3h3aGhzNDUza2N3dTVOWjhPMUdKV2IzL1BWdDdOWU9OZnQ1L2RSbXJpMzc4ZXB2M1ZrWCtPOS9lS3RzaS9kd3AxSVdDQXBZYXZobUl4SFVwUHgwcXZHT09uZkQ1SXZiQmRnM2pkdUpjbXR2cE9iL1RRcHlmc3dEdUJmNzB5bUc4YnZSU1AyeUNucC9UYTkrTVEvSER0Zk5ReGVIZDVnbWVvNjV6Mm9iUEYwL1o2MG5EQUxxMjF3YTVqN3VRZXVCdGU2enJhVSsvcDU2ZmlEdXpMb0RhMjYzNXZ5SFJzTDI1TTNkbWU1bDBQeUpYT2I2QnRPalU4VzdCUkNMbHc0Mnl6T3ZwYXRPSHl3Y3FiN2V1dUF2OHo5OGJXOFhmMlhvKzZqL1docnFmZGNhMTNNM0xhMDVFemVCeFAybDlNdlhsK1NJYmpqeSszanhmSjNmYWdidVI3YTg3WVdiTXU3UDN5TUtuM2Z1bGN5ZUszLzNmaWR4MTBKcjNCL1JUT2hWRjljTzg0RnlZTzlqelN3NXJmZjNBNjQ4ZDZzSmk5ZHBiT09yaWYxSGZuQjhnZTZiTkNRZzNsSVBPajlxY0wvcHcxZXMvemh2UHFiVnNOUEJ2VS9LbGVDelplYzNSVVM2R1diUDVHTGFHanF5Rmdhajg4VElhM0R4Ty9ONnV4eDFsdE1kWjJkV3J0OGRqV1dqaVhYbDRYKzdQb00vajh2NlUzM2JIYjdrdmZBYkJTSkZiNktaRjBkem5SSFYzV3YzOHJ3YjEwYloreUczTTVYeWtkWExQUHpWeWc5UFlHUW1pb3V3Njlpd0szc2dyaEFsUzRmS3RtQllmNFZYQ2NLdHRFaUpRejZMTlVKUVI0S3RMWFp6MHVTRGpWY3dkU3RVaXJscHU5c09YZGhPVmVpVXpUWUFoQzR0TWsvTWZrV2ZaRmdXYjg4SS9KTTl3emNib1pnSXRmSUdMVE9tV1h3N3laMFRmRG5GTzRxcnB3TmExdW1wVmFOYjB2ajNjN3kzcUNVWWFXS09PWm80YU9QaVJ3KzAzTXBDYmprN0grdXY5WTlseTMvYkc1ckxsdTg0QkRqa09yYklSckZCQXZxcnI1MktyK2xSQzUvUXJYNFFnYUhpSE4zajhoV0R4R2JlaTltZGhiMHBVT25IOHJ1SmVHZUg4RGZsYmFObWJSS0dvZi82WHYyVkpBWHg5VEczR1orZlJqdkkyMFc0VDdEQktQSms2Mmw4dXYvMVp5SW0wdlZXQjV5TjllMTc4ZE1CdkNxdTJEQ2kyRnVmcHoyN0JrV1BTUXhkak10KzFEWWNabU1pZC8rU242alBEL3grOUZpVHJHZjNrL3NWTXZaZm9qeG9TbkcwTjRKbGdQWGI2dkVVOUNGK3BCUWFOYXBXa2FFUnJxTElRMzBDc2s0YkhRT3hJS3VmMzdBbHQxYUg4S0lzMDdDRFd0Mk5jSVFobWFVZjlZL29YK2p2THZQL2FTNGFVTHFCNXJBYW9USm9RT1UvdkREZ0w5M2J3bzI0RFNRTTk4VXNxWTduaDNYU3JhVUl5ZThtU0haTmh0eUVoUS9pMmJzcFJ4RkozdDZhRWNsODFnNmFtQ3hvU2dVOG1ZWS82UmdxVTNjenI4bFZDcFp5eS8yMEN4bnROcHVtL3BmeDZjdWVxRk1jQXZ4K2ZCc09EbndxNjRHVEptUGYyN3dVUmhLckhyNzkvMzhjTXZqMWZuTm1zQklRZitHaDY4Slp4d1QyNGo4R1RsR0tNUmVSd0NHUlJoNWpibTBRd1E3RWpRNmQrYm1VYXU0SDRLWVlGQnBDUXVOUGFJQzRBdUU4NzF2aUxjdmY5Y0lhREI1MFVIeGpJaDRLWlRzNVpqb0wxakhHZDJIbU96cXppVzBaZzlrQ1pSTnIzUEpzM1V6a2QzYklxekczM1lJUmN6bmJyeVI2bGJiOVpxT2xVM1ZNWUpvQTZFK2xXNGp0MkNidlRBZkRLNlBSZ2x2Y1AyRHM4dmxlTktsbTM2ck5HbCtjMlVaMjBCekxNUjVOdi9BSGFUd2pSQUdRQUEifQ==",
    "DCOS_PACKAGE_VERSION": "4.5.2-0.3",
    "DCOS_PACKAGE_NAME": "grafana",
    "DCOS_PACKAGE_IS_FRAMEWORK": "false"
  },
  "maxLaunchDelaySeconds": 3600,
  "mem": 512,
  "gpus": 0,
  "networks": [
    {
      "mode": "container/bridge"
    }
  ],
  "requirePorts": false,
  "upgradeStrategy": {
    "maximumOverCapacity": 1,
    "minimumHealthCapacity": 1
  },
  "killSelection": "YOUNGEST_FIRST",
  "unreachableStrategy": {
    "inactiveAfterSeconds": 300,
    "expungeAfterSeconds": 600
  },
  "fetch": [],
  "constraints": []
}
```

> Please note that if you are *not* on a VPN and running an AWS cluster, you may need to translate the host IP shown on Grafana instance to public IP and may need to open the port through appropriate configuration of *Security Groups* on AWS console.

Once you are logged into Grafana, the following steps need to be followed to set up a dashboard for the Network Intrusion Anomaly Detection visualization:

**Step 1: Setup Datasource.** Go to the Datasource set up page and start creating a data source. Enter the following details:

* Name of the datasource = `nwintrusion` 
* Type = `InfluxDB`
* Http Settings Url = `http://influx-db.marathon.l4lb.thisdcos.directory:8086` 
* Http Settings Access = `proxy`
* InfluxDB Details Database = `anomaly`
* InfluxDB Details User = `root`, Password = `root`

Save the datasource and ensure that it's working. A message will come once you click Test and Save.

**Step 2: Import Dashboard.** The distribution (`bin/nwintrusion`) contains a sample dashboard json for Grafana named `nwintrusion-grafana-dashboard.json.sample`. Go to the dashboard ceation page in Grafana and import this json to set up a sample dashboard. If you want to do further customizations, you can do it on top of this basic dashboard.

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
