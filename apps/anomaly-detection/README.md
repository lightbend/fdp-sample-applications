# Anomaly Detection using Deep Learning

This application demonstrates a full lifecycle of anomaly detection using supervised learning developed using deep learning and leveraging [Intel BigDL](https://software.intel.com/en-us/articles/bigdl-distributed-deep-learning-on-apache-spark) as the implementation framework. 

The application consists of the following modules:

* **Data publisher:** Generates simulated CPU signal data based on a probability distribution and publishes to Kafka
* **Data collector:** Collects data from Kafka and stores them in InfluxDB for use by machine learning and visualization
* **Data ingester:** Ingests data from InfluxDB and prepares them in CSV format for model training
* **Model training:** Trains a deep learning model 
* **Model publisher:** Publishes the trained model and some useful statictics to Kafka to be used by the model server
* **Model serving:** Serving models in real time and updating them as new models will become available. Its based on the [minibook](https://www.lightbend.com/blog/serving-machine-learning-models-free-oreilly-ebook-from-lightbend) 
* **Speculative model serving:** Serving models, leveraging speculative execution and voting decision making, in real time and updating them as new models will become available. Based on this [blog post](https://developer.lightbend.com/blog/2018-05-24-speculative-model-serving/index.html)


# Installation

## Installing Data Publisher


## Installing Data Collector


## Installing all Training Modules

The three training modules that need to be installed are:

* Data ingester (pre-training)
* Model training
* Model publisher (post-training)

All of the above modules are installed on a DC/OS pod as 3 images. The JSON that has to be used for deployment is located in `.../fdp-sample-applications/apps/anomaly-detection/bin`. It can be deployed using the command `dcos marathon pod add training-pod.json`.

> This installation procedure assumes that the previous 2 installtion steps have been completed and all prerequisites (Kafka and InfluxDB) running in the cluster.

### Preparing docker image for Data Ingester

This can be done using `sbt`. The steps are as follows:

```
$ pwd
.../fdp-sample-applications/apps/anomaly-detection
$ sbt
sbt:AnomalyDetection> projects
[info] In file: .../fdp-sample-applications/apps/anomaly-detection/
[info] 	   admodelserver
[info] 	   adpublisher
[info] 	   adspeculativemodelserver
[info] 	 * anomalyDetection
[info] 	   configuration
[info] 	   influxSupport
[info] 	   kafkaSupport
[info] 	   model
[info] 	   protobufs
[info] 	   trainingdataingestion
[info] 	   trainingmodelpublish
sbt:AnomalyDetection> project trainingdataingestion
[info] Set current project to trainingdataingestion (in build file: ..)
sbt:AnomalyDetection> docker
...
sbt:AnomalyDetection> dockerPush

``` 

This will push the docker image to the repository sepcified in the build file. You need to change it for your own repository name.

### Preparing docker image for Model Training

The docker image for model training uses Intel's Analytics Zoo and the image is also derived fom the base image of Intel. The detailed steps of how to build the docker image is specified in the `README` file under `.../fdp-sample-applications/apps/anomaly-detection/analytics-zoo/apps/python/lightbend`.


### Preparing docker image for Model Publisher

This can be done using `sbt`. The steps are as follows:

```
$ pwd
.../fdp-sample-applications/apps/anomaly-detection
$ sbt
sbt:AnomalyDetection> projects
[info] In file: .../fdp-sample-applications/apps/anomaly-detection/
[info] 	   admodelserver
[info] 	   adpublisher
[info] 	   adspeculativemodelserver
[info] 	 * anomalyDetection
[info] 	   configuration
[info] 	   influxSupport
[info] 	   kafkaSupport
[info] 	   model
[info] 	   protobufs
[info] 	   trainingdataingestion
[info] 	   trainingmodelpublish
sbt:AnomalyDetection> project trainingmodelpublish
[info] Set current project to trainingmodelpublish (in build file: ..)
sbt:AnomalyDetection> docker
...
sbt:AnomalyDetection> dockerPush

``` 

This will push the docker image to the repository sepcified in the build file. You need to change it for your own repository name.

Once all the docker images are prepared, check if the image names tally with the one in `training-pod.json`. Change if required and deploy to the DC/OS cluster.



