# Utilities and definitions shared by several scripts.

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

## dcos spark run --submit-args='--conf spark.mesos.uris=https://s3-us-west-2.amazonaws.com/andrey-so-36323287/pi.conf --class JavaSparkPiConf https://s3-us-west-2.amazonaws.com/andrey-so-36323287/sparkPi_without_config_file.jar /mnt/mesos/sandbox/pi.conf'


function run_anomaly_detection_spark_job {
  local NO_OF_CLUSTERS=$1
  local CLUSTERING_MICRO_BATCH_DURATION=$2
  local ZEPPELIN=$3
  local SPARK_APP_CLASS="com.lightbend.fdp.sample.nwintrusion.SparkClustering"
  local SPARK_CONF="--conf spark.cores.max=2 --conf spark.streaming.kafka.consumer.poll.ms=10000 --conf spark.streaming.kafka.consumer.cache.enabled=false --conf spark.mesos.uris=http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/endpoints/core-site.xml,http://api.hdfs.marathon.l4lb.thisdcos.directory/v1/endpoints/hdfs-site.xml,$LABORATORY_MESOS_PATH/influx.conf --driver-memory 8G"
  local ARGS="/mnt/mesos/sandbox/influx.conf $KAFKA_TO_TOPIC $KAFKA_BROKERS $CLUSTERING_MICRO_BATCH_DURATION $NO_OF_CLUSTERS"
  local SPARK_APP_JAR_URL="$LABORATORY_MESOS_PATH/$SPARK_APP_JAR"

  if [[ "$ZEPPELIN" == yes ]]; then
    printf "\nIf you want to try out the anomaly detection app in Zeppelin, set the following parameters in the Zeppelin notebook 'FDP Sample Apps/SparkClustering'
val noOfClusters = $NO_OF_CLUSTERS
val topicToReadFrom = Array(\"$KAFKA_TO_TOPIC\")
val broker = \"$KAFKA_BROKERS\"
val duration = Seconds($CLUSTERING_MICRO_BATCH_DURATION)\n"
  else
      echo "  k = $DEFAULT_NO_OF_CLUSTERS"
      echo "  micro batch duration = $DEFAULT_CLUSTERING_MICRO_BATCH_DURATION seconds"

      local SUBMIT="$($NOEXEC dcos spark run --submit-args="$SPARK_CONF --class $SPARK_APP_CLASS $SPARK_APP_JAR_URL $ARGS")"
      if [ -z "$NOEXEC" ]
      then
        ANOMALY_DETECTION_SPARK_DRIVER_SUBMIT_ID="$(echo `expr "$SUBMIT" : '.*\(driver-.*\)'`)"
        $NOEXEC update_json_field ANOMALY_DETECTION_SPARK_DRIVER_SUBMIT_ID "$ANOMALY_DETECTION_SPARK_DRIVER_SUBMIT_ID" "$APP_METADATA_FILE"
        show_submission_message "$ANOMALY_DETECTION_SPARK_DRIVER_SUBMIT_ID"
      else
        echo "$SUBMIT"
      fi
  fi
}

function run_batch_kmeans_spark_job {
  local FROM_CLUSTER_COUNT=$1
  local TO_CLUSTER_COUNT=$2
  local INCREMENT=$3
  local CLUSTERING_MICRO_BATCH_DURATION=$4
  local ZEPPELIN=$5
  local SPARK_APP_CLASS="com.lightbend.fdp.sample.nwintrusion.BatchKMeans"
  local SPARK_CONF="--conf spark.cores.max=2 --conf spark.streaming.kafka.consumer.poll.ms=10000 --conf spark.streaming.kafka.consumer.cache.enabled=false --driver-memory 8G"
  local ARGS="$KAFKA_TO_TOPIC $KAFKA_BROKERS $CLUSTERING_MICRO_BATCH_DURATION $FROM_CLUSTER_COUNT $TO_CLUSTER_COUNT $INCREMENT"
  local SPARK_APP_JAR_URL="$LABORATORY_MESOS_PATH/$SPARK_APP_JAR"

  if [[ "$ZEPPELIN" == yes ]]; then
    printf "\nIf you want to try out the Batch K-Means app in Zeppelin, set the following parameters in the Zeppelin notebook 'FDP Sample Apps/BatchKMeans'
val topicToReadFrom = Array(\"$KAFKA_TO_TOPIC\")
val broker = \"$KAFKA_BROKERS\"
val microbatchDuration = Seconds($CLUSTERING_MICRO_BATCH_DURATION)
val fromClusterCount = $FROM_CLUSTER_COUNT
val toClusterCount = $TO_CLUSTER_COUNT
val increment = $INCREMENT\n"
  else
      echo "  micro batch duration = $DEFAULT_OPTIMAL_K_CLUSTERING_MICRO_BATCH_DURATION"
      echo "  Trying K between $DEFAULT_OPTIMAL_K_FROM_CLUSTER_COUNT and $DEFAULT_OPTIMAL_K_TO_CLUSTER_COUNT (inclusive)"
      echo "  Delta between K values = $DEFAULT_OPTIMAL_K_INCREMENT"
      local SUBMIT="$($NOEXEC dcos spark run --submit-args="$SPARK_CONF --class $SPARK_APP_CLASS $SPARK_APP_JAR_URL $ARGS")"
      if [ -z "$NOEXEC" ]
      then
        BATCH_KMEANS_SPARK_DRIVER_SUBMIT_ID="$(echo `expr "$SUBMIT" : '.*\(driver-.*\)'`)"
        $NOEXEC update_json_field BATCH_KMEANS_SPARK_DRIVER_SUBMIT_ID "$BATCH_KMEANS_SPARK_DRIVER_SUBMIT_ID" "$APP_METADATA_FILE"
        show_submission_message "$BATCH_KMEANS_SPARK_DRIVER_SUBMIT_ID"
      else
        echo "$SUBMIT"
      fi
  fi
}

