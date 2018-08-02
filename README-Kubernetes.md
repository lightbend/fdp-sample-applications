This file preserves the documentation about using Kubernetes, but it's here instead
of the README, because we aren't offering a preview of Kubernetes support for the
sample apps yet. When we're ready, we'll merge this content into the README.md.

## Deploying KillrWeather to Kubernetes.

> **Note:** Kubernetes support is documented here for
Deploying Killrweather to Kubernetes requires it to be upgraded to Spark 2.3. Once this is done,
deployment is based on [Spark on Kubernetes](https://spark.apache.org/docs/latest/running-on-kubernetes.html). Additional information can be find
[Medium article](https://medium.com/@timfpark/cloud-native-big-data-jobs-with-spark-2-3-and-kubernetes-938b04d0da57) and
[Banzai series of articles](https://banzaicloud.com/blog/spark-k8s-internals/).
Following this publications deployment is based on 3 main staps:
1. Creation of the base Spark image for kubernetes. The image `lightbend/spark:k8s-rc` is currently published to the docker registry (this is a build from Spark repo main). This image can be used as a base image for
KillrWeather or any other Spark based application deployed to kubernetes
2. Creation of Killrweather image for running in Kubernetes. The `Dockerfile` is added to the project for docker creation.
This just adds Killrweather assembly jar to the Spark image. It also creates a directory for downloading HDFS configuration files and
sets HADOOP_CONF_DIR to this directory. Hadoop integration is required here for checkpointing. As an alternative we are showing how to deploy 
Spark applications using [Kubernetes PVCs](https://kubernetes.io/docs/concepts/storage/persistent-volumes/) instead of HDFS.
3. Leverage `spark-submit` for running an application (replace `X.Y.Z` with the correct FDP version, e.g., `1.2.3`)

````
/opt/spark/bin/spark-submit
        --master k8s://http://kube-apiserver-0-instance.kubernetes.mesos:9000
        --deploy-mode cluster
        --name killrweather
        --class com.lightbend.killrweather.app.KillrWeather
        --conf spark.executor.instances=3
        --files http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/endpoints/hdfs-site.xml,http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/endpoints/core-site.xml
        --conf spark.kubernetes.mountDependencies.filesDownloadDir=/etc/hadoop/conf
        --conf 'spark.driver.extraJavaOptions=-Dconfig.resource=cluster.conf'
        --conf 'spark.executor.extraJavaOptions=-Dconfig.resource=cluster.conf'
        --conf spark.kubernetes.container.image=lightbend/fdp-killrweather-app-k8s:X.Y.Z
        --conf spark.kubernetes.container.image.pullPolicy=Always
        --conf spark.kubernetes.driverEnv.KAFKA_BROKERS=broker.kafka.l4lb.thisdcos.directory:9092
        --conf spark.kubernetes.driverEnv.CASSANDRA_HOSTS=node-0-server.cassandra.autoip.dcos.thisdcos.directory, node-1-server.cassandra.autoip.dcos.thisdcos.directory, node-2-server.cassandra.autoip.dcos.thisdcos.directory
        --conf spark.kubernetes.driverEnv.SPARK_BATCH_INTERVAL=5 second
        --conf spark.kubernetes.driverEnv.CHECKPOINT_DIRECTORY=file:///usr/checkpoint/
        --conf spark.kubernetes.driverEnv.GRAFANA_HOST=grafana.marathon.l4lb.thisdcos.directory
        --conf spark.kubernetes.driverEnv.GRAFANA_PORT=3000
        --conf spark.kubernetes.driverEnv.INFLUXDB_HOST=http://influxdb.marathon.l4lb.thisdcos.directory
        --conf spark.kubernetes.driverEnv.INFLUXDB_PORT=8086
        --conf 'spark.executorEnv.KAFKA_BROKERS=broker.kafka.l4lb.thisdcos.directory:9092'
        --conf 'spark.executorEnv.CASSANDRA_HOSTS=node-0-server.cassandra.autoip.dcos.thisdcos.directory, node-1-server.cassandra.autoip.dcos.thisdcos.directory, node-2-server.cassandra.autoip.dcos.thisdcos.directory'
        --conf 'spark.executorEnv.SPARK_BATCH_INTERVAL=5 second'
        --conf 'spark.executorEnv.CHECKPOINT_DIRECTORY=file:///usr/checkpoint/'
        --conf 'spark.executorEnv.GRAFANA_HOST=grafana.marathon.l4lb.thisdcos.directory'
        --conf 'spark.executorEnv.GRAFANA_PORT=3000'
        --conf 'spark.executorEnv.INFLUXDB_HOST=http://influxdb.marathon.l4lb.thisdcos.directory'
        --conf 'spark.executorEnv.INFLUXDB_PORT=8086'
        local:///opt/spark/jars/fdp-killrweather-app-assembly:X.Y.Z.jar"
````

There are several important lines in this submit command:
1. Master has to point to the k8 API server (I am using API server 0)
2. Only cluster deploy mode is currently supported.
3. Files allows to load HDFS definition from Marathon
4. `spark.kubernetes.mountDependencies.filesDownloadDir` sets the location where files are loaded
5. Reference to the assembly has to be `local` meaning that the data is picked up from the docker image and location is the location in the docker image.

## Deploying with chart

Two deployment charts are created for deployment 
* `killrweather-deployment-chart-hdfs` deploying killrweather leveraging HDFS for checkpointing.
* `killrweather-deployment-chart-pvc` deploying killrweather leveraging PVCs (we used [GlusterFS](https://docs.gluster.org/en/v3/Administrator%20Guide/GlusterFS%20Introduction/)) for checkpointing.

It can be installed using the following 
command `helm install ./killrweather-deployment-chart-hdfs` or `helm install ./killrweather-deployment-chart-hdfs`


