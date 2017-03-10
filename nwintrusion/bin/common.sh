# Utilities and definitions shared by several scripts.

KAFKA_DCOS_PACKAGE="kafka"
TRANSFORM_DATA_TEMPLATE_FILE="$DIR/transform-data.json.template"
TRANSFORM_DATA_TEMPLATE=${TRANSFORM_DATA_TEMPLATE_FILE%.*}
LOAD_DATA_TEMPLATE_FILE="$DIR/load-data.json.template"
LOAD_DATA_TEMPLATE=${LOAD_DATA_TEMPLATE_FILE%.*}
VIS_DATA_TEMPLATE_FILE="$DIR/visualize-data.json.template"
VIS_DATA_TEMPLATE=${VIS_DATA_TEMPLATE_FILE%.*}
SPARK_APP_JAR="fdp-nw-intrusion-assembly-0.1.jar"
AWS_ENV_FILE=$HOME/.ssh/aws.sh

# load-data.json variables
KAFKA_FROM_TOPIC="nwin"
KAFKA_TO_TOPIC="nwout"
KAFKA_ERROR_TOPIC="nwerr"
KAFKA_CLUSTERS_TOPIC="nwcls"
# Note: underscore is not allowed as part of the id, marathon's restriction
TRANSFORM_DATA_APP_ID="nwin-transform-data"
LOAD_DATA_APP_ID="nwin-load-data"
VIS_DATA_APP_ID="nwin-visualize-data"

ZOOKEEPER_PORT=2181
DOCKER_USERNAME=lightbend
KAFKA_BROKERS=

APP_METADATA_FILE="$DIR/app.metadata.json"

# Default command-line arguments for the Spark Streaming
# anomaly detection app.
DEFAULT_NO_OF_CLUSTERS=30
DEFAULT_CLUSTERING_MICRO_BATCH_DURATION=1

# Default command-line arguments for the Spark app to determine
# the best value of K (the number of clusters)
DEFAULT_OPTIMAL_K_CLUSTERING_MICRO_BATCH_DURATION=60
DEFAULT_OPTIMAL_K_FROM_CLUSTER_COUNT=10
DEFAULT_OPTIMAL_K_TO_CLUSTER_COUNT=100
DEFAULT_OPTIMAL_K_INCREMENT=10

# Used by --no-exec option
NOEXEC=

function okay_to_proceed {
  [ $# -gt 0 ] && echo "$@"
  echo "Okay to proceed? [Y/n]"
  read answer
  if [ -z "$answer" -o "$answer" = 'y' -o "$answer" = 'Y' ]
  then
    return 0
  else
    return 1
  fi
}

# For multi-line output, define an array of strings, e.g.,
#   msg=("line one" "line two")
# and pass the array as follows:
#   error ${msg[@]}
# Using `error $msg` won't print properly!
function error {
  echo >&2
  echo "ERROR: " >&2
  for s in "$@"
  do
    echo "  $s" >&2
    shift
  done
  echo >&2
  show_help
  exit 1
}

function env_error {
  show_help
  exit 1
}

function gather_kafka_connection_info {
  KAFKA_CONN_INFO="$(dcos $KAFKA_DCOS_PACKAGE connection)"
  KAFKA_BROKERS="$(echo $KAFKA_CONN_INFO | jq -r '.dns[0]')"
}

function update_json_field {
  local JSON_FIELD="$1"
  local JSON_NEW_VALUE="$2"
  local JSON_FILE="$3"
  jq ".$JSON_FIELD=\"$JSON_NEW_VALUE\"" $JSON_FILE > tmp.$$.json && mv tmp.$$.json $JSON_FILE
}

function add_to_json_array {
  local JSON_FIELD="$1"
  local JSON_NEW_VALUE="$2"
  local JSON_FILE="$3"
  jq ".$1 |= .+ [\"$JSON_NEW_VALUE\"]" $JSON_FILE > tmp.$$.json && mv tmp.$$.json $JSON_FILE
}

function show_help {
  cat<< EOF

  $HELP_MESSAGE

  Usage: $SCRIPT  [OPTIONS]

  eg: ./$SCRIPT $HELP_EXAMPLE_OPTIONS

  Options:
$HELP_OPTIONS
  -n | --no-exec              Do not actually run commands, just print them (for debugging).
  -h | --help                 Prints this message.
EOF
}

function require_dcos_cli {
  set +e
  type dcos >/dev/null 2>&1 || {
    error "The DC/OS CLI is required but it is not installed. Please install and retry...";
  }
  set -e
}

function require_spark {
  set +e
  dcos spark --version > /dev/null 2>&1 || {
    error "DC/OS spark CLI subcommand is required but it is not installed. Please install and retry..."
  }
  set -e
}

function require_kafka {
  set +e
  case "$KAFKA_DCOS_PACKAGE" in
    kafka|confluent-kafka) ;;  # okay
    *)
      msg=(
        "Invalid value for Kafka DC/OS package: $KAFKA_DCOS_PACKAGE"
        "Allowed values are kafka (default) and confluent-kafka")
      error "${msg[@]}"
      ;;
  esac

  dcos $KAFKA_DCOS_PACKAGE --version > /dev/null 2>&1 || {
    error "The dcos $KAFKA_DCOS_PACKAGE subcommand is required but it is not installed. Please install and retry..."
  }
  set -e
}

function require_jq {
  set +e
  type jq >/dev/null 2>&1 || install_jq
  set -e
}

function install_jq {
  okay_to_proceed "Need to install the 'jq' tool on your workstation. It's a command line JSON processor."
  if [ $? -ne 0]
  then
    echo "Quiting now."
    exit 1
  fi

  case $(uname) in
    Linux*)
    $NOEXEC sudo apt-get -y jq
    ;;
    Darwin*)
    $NOEXEC brew install jq
    ;;
    *)
    echo "Unsupported or unrecognized OS (output of uname = $(uname)). Please install jq."
    exit 1
    ;;
  esac
}

function require_auth {
  if [ -z $WITH_IAM_ROLE ]; then
    if [ -z $AWS_ACCESS_KEY_ID ]; then
      echo "AWS_ACCESS_KEY_ID not defined: Trying $AWS_ENV_FILE"
      [ -f $AWS_ENV_FILE ] && source $AWS_ENV_FILE
      if [ -z $AWS_ACCESS_KEY_ID ]; then
        env_error "AWS_ACCESS_KEY_ID"
      fi
    fi

    if [ -z $AWS_SECRET_ACCESS_KEY ]; then
      echo "AWS_SECRET_ACCESS_KEY not defined: Trying $AWS_ENV_FILE"
      [ -f $AWS_ENV_FILE ] && source $AWS_ENV_FILE
      if [ -z $AWS_SECRET_ACCESS_KEY ]; then
        env_error "AWS_SECRET_ACCESS_KEY"
      fi
    fi

    echo "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY are defined"
  fi
}

function require_templates {
  # check if transform data file is available
  if [ ! -f  "$TRANSFORM_DATA_TEMPLATE_FILE" ]; then
    msg=("$TRANSFORM_DATA_TEMPLATE_FILE is missing or is not a file."
         "Please copy the file in $DIR or use package.sh to properly setup this app.")
    error "${msg[@]}"
  fi

  # check if load data template file is available
  if [ ! -f  "$LOAD_DATA_TEMPLATE_FILE" ]; then
    msg=("$LOAD_DATA_TEMPLATE_FILE is missing or is not a file."
         "Please copy the file in $DIR or use package.sh to properly setup this app.")
    error "${msg[@]}"
  fi
}

function show_submission_message {
  echo "Submission ID: $1"
  echo "To tail the logs, run this DC/OS CLI command:"
  echo "  dcos spark log $1 --follow"
}

function run_anomaly_detection_spark_job {
  local NO_OF_CLUSTERS=$1
  local CLUSTERING_MICRO_BATCH_DURATION=$2
  local APP_S3_URL=$3
  local SPARK_APP_CLASS="com.lightbend.fdp.sample.SparkClustering"
  local SPARK_CONF="--conf spark.cores.max=2 --conf spark.streaming.kafka.consumer.poll.ms=10000"
  local ARGS="$KAFKA_TO_TOPIC $KAFKA_BROKERS $KAFKA_CLUSTERS_TOPIC $CLUSTERING_MICRO_BATCH_DURATION $NO_OF_CLUSTERS"
  local SPARK_APP_JAR_URL="$APP_S3_URL/$SPARK_APP_JAR"
  local SUBMIT="$($NOEXEC dcos spark run --submit-args="$SPARK_CONF --class $SPARK_APP_CLASS $SPARK_APP_JAR_URL $ARGS")"

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
  local APP_S3_URL=$5
  local SPARK_APP_CLASS="com.lightbend.fdp.sample.BatchKMeans"
  local SPARK_CONF="--conf spark.cores.max=2 --conf spark.streaming.kafka.consumer.poll.ms=10000 --driver-memory 8G"
  local ARGS="$KAFKA_TO_TOPIC $KAFKA_BROKERS $CLUSTERING_MICRO_BATCH_DURATION $FROM_CLUSTER_COUNT $TO_CLUSTER_COUNT $INCREMENT"
  local SPARK_APP_JAR_URL="$APP_S3_URL/$SPARK_APP_JAR"
  local SUBMIT="$($NOEXEC dcos spark run --submit-args="$SPARK_CONF --class $SPARK_APP_CLASS $SPARK_APP_JAR_URL $ARGS")"

  if [ -z "$NOEXEC" ]
  then
    BATCH_KMEANS_SPARK_DRIVER_SUBMIT_ID="$(echo `expr "$SUBMIT" : '.*\(driver-.*\)'`)"
    $NOEXEC update_json_field BATCH_KMEANS_SPARK_DRIVER_SUBMIT_ID "$BATCH_KMEANS_SPARK_DRIVER_SUBMIT_ID" "$APP_METADATA_FILE"
    show_submission_message "$BATCH_KMEANS_SPARK_DRIVER_SUBMIT_ID"
  else
    echo "$SUBMIT"
  fi
}

