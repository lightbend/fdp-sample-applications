# Utilities and definitions shared by several scripts.

. "$DIR/../../version.sh"
. "$DIR/../../bin/common.sh"

KAFKA_DCOS_PACKAGE="kafka"

TRANSFORM_DATA_TEMPLATE_FILE="$DIR/transform-data.json.template"
TRANSFORM_DATA_TEMPLATE=${TRANSFORM_DATA_TEMPLATE_FILE%.*}
AWS_ENV_FILE=$HOME/.ssh/aws.sh

# load-data.json variables
KAFKA_FROM_TOPIC="nwin"
KAFKA_TO_TOPIC="nwout"
KAFKA_ERROR_TOPIC="nwerr"
# Note: underscore is not allowed as part of the id, marathon's restriction
TRANSFORM_DATA_APP_ID="nwin-transform-data"

ZOOKEEPER_PORT=2181
DOCKER_USERNAME=lightbend

ANOMALY_DETECTION_DOCKER_IMAGE=anomalydetection
ANOMALY_DETECTION_JAR=anomalyDetection-assembly
BATCH_KMEANS_DOCKER_IMAGE=batchkmeans
BATCH_KMEANS_JAR=batchKMeans-assembly

KAFKA_BROKERS=

# Default command-line arguments for the Spark Streaming
# anomaly detection app.
DEFAULT_NO_OF_CLUSTERS=150
DEFAULT_CLUSTERING_MICRO_BATCH_DURATION=1

# Default command-line arguments for the Spark app to determine
# the best value of K (the number of clusters)
DEFAULT_OPTIMAL_K_CLUSTERING_MICRO_BATCH_DURATION=60
DEFAULT_OPTIMAL_K_FROM_CLUSTER_COUNT=10
DEFAULT_OPTIMAL_K_TO_CLUSTER_COUNT=100
DEFAULT_OPTIMAL_K_INCREMENT=10

function require_templates {
  # check if transform data file is available
  if [ ! -f  "$TRANSFORM_DATA_TEMPLATE_FILE" ]; then
    msg=("$TRANSFORM_DATA_TEMPLATE_FILE is missing or is not a file."
         "Please copy the file in $DIR or use package.sh to properly setup this app.")
    error "${msg[@]}"
  fi
}

## $ dcos spark run --submit-args="--conf spark.mesos.appJar.local.resolution.mode=container --conf spark.executor.memory=1g --conf spark.mesos.executor.docker.image=skonto/spark-local:test --conf spark.executor.cores=2 --conf spark.cores.max=8 --class org.apache.spark.examples.SparkPi local:///spark-examples_2.11-2.2.1.jar"


function run_anomaly_detection_spark_job {

  local NO_OF_CLUSTERS=$1
  local CLUSTERING_MICRO_BATCH_DURATION=$2
  local SPARK_APP_CLASS="com.lightbend.fdp.sample.nwintrusion.anomaly.SparkClustering"

  local SPARK_CONF="--conf spark.mesos.appJar.local.resolution.mode=container --conf spark.cores.max=2 --conf spark.streaming.kafka.consumer.poll.ms=10000 --conf spark.streaming.kafka.consumer.cache.enabled=false --conf spark.mesos.executor.docker.image=$DOCKER_USERNAME/$ANOMALY_DETECTION_DOCKER_IMAGE:$VERSION --conf spark.mesos.uris=http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/endpoints/core-site.xml,http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/endpoints/hdfs-site.xml --driver-memory 8G"

  local ARGS="-t $KAFKA_TO_TOPIC -b $KAFKA_BROKERS -m $CLUSTERING_MICRO_BATCH_DURATION -k $NO_OF_CLUSTERS --with-influx"

  echo "  k = $NO_OF_CLUSTERS"
  echo "  micro batch duration = $CLUSTERING_MICRO_BATCH_DURATION seconds"

  echo dcos spark run --name=spark --submit-args="$SPARK_CONF --class $SPARK_APP_CLASS local:///opt/spark/dist/jars/$ANOMALY_DETECTION_JAR-$VERSION.jar $ARGS"

  local SUBMIT="$($NOEXEC dcos spark run --name=spark --submit-args="$SPARK_CONF --class $SPARK_APP_CLASS local:///opt/spark/dist/jars/$ANOMALY_DETECTION_JAR-$VERSION.jar $ARGS")"

  if [ -z "$NOEXEC" ]
    then
      ANOMALY_DETECTION_SPARK_DRIVER_SUBMIT_ID="$(echo `expr "$SUBMIT" : '.*\(driver-.*\)'`)"
      $NOEXEC update_json_field ANOMALY_DETECTION_SPARK_DRIVER_SUBMIT_ID "$ANOMALY_DETECTION_SPARK_DRIVER_SUBMIT_ID" "$APP_METADATA_FILE"
      show_submission_message "$ANOMALY_DETECTION_SPARK_DRIVER_SUBMIT_ID"
    else
      echo "$SUBMIT"
  fi
}

function run_batch_kmeans_spark_job {
  local FROM_CLUSTER_COUNT=$1
  local TO_CLUSTER_COUNT=$2
  local INCREMENT=$3
  local CLUSTERING_MICRO_BATCH_DURATION=$4
  local SPARK_APP_CLASS="com.lightbend.fdp.sample.nwintrusion.batchkmeans.BatchKMeans"

  local SPARK_CONF="--conf spark.mesos.appJar.local.resolution.mode=container --conf spark.cores.max=2 --conf spark.streaming.kafka.consumer.poll.ms=10000 --conf spark.streaming.kafka.consumer.cache.enabled=false --conf spark.mesos.executor.docker.image=$DOCKER_USERNAME/$BATCH_KMEANS_DOCKER_IMAGE:$VERSION --driver-memory 8G"

  local ARGS="-t $KAFKA_TO_TOPIC -b $KAFKA_BROKERS -m $CLUSTERING_MICRO_BATCH_DURATION -f $FROM_CLUSTER_COUNT -c $TO_CLUSTER_COUNT -i $INCREMENT"

  echo "  micro batch duration = $CLUSTERING_MICRO_BATCH_DURATION"
  echo "  Trying K between $FROM_CLUSTER_COUNT and $TO_CLUSTER_COUNT (inclusive)"
  echo "  Delta between K values = $INCREMENT"

  echo dcos spark run --submit-args="$SPARK_CONF --class $SPARK_APP_CLASS local:///opt/spark/dist/jars/$BATCH_KMEANS_JAR-$VERSION.jar $ARGS"

  local SUBMIT="$($NOEXEC dcos spark run --submit-args="$SPARK_CONF --class $SPARK_APP_CLASS local:///opt/spark/dist/jars/$BATCH_KMEANS_JAR-$VERSION.jar $ARGS")"

  if [ -z "$NOEXEC" ]
  then
    BATCH_KMEANS_SPARK_DRIVER_SUBMIT_ID="$(echo `expr "$SUBMIT" : '.*\(driver-.*\)'`)"
    $NOEXEC update_json_field BATCH_KMEANS_SPARK_DRIVER_SUBMIT_ID "$BATCH_KMEANS_SPARK_DRIVER_SUBMIT_ID" "$APP_METADATA_FILE"
    show_submission_message "$BATCH_KMEANS_SPARK_DRIVER_SUBMIT_ID"
  else
    echo "$SUBMIT"
  fi
}

