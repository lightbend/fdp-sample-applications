#!/usr/bin/env bash
set -e
# set -x

SCRIPT=$(basename "${BASH_SOURCE[0]}")

## run directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../bin/common.sh"
. "$DIR/utils.sh"

## project root directory
PROJ_ROOT_DIR="$( cd "$DIR/../source/core" && pwd -P )"

## deploy.conf full path
DEPLOY_CONF_FILE="$PROJ_ROOT_DIR/deploy.conf"

ZOOKEEPER_PORT=2181

VERSIONED_PROJECT_NAME=
VERSIONED_NATIVE_PACKAGE_NAME=

# Used by show_help
HELP_MESSAGE="Installs the network intrusion app. Assumes DC/OS authentication was successful
  using the DC/OS CLI."
HELP_EXAMPLE_OPTIONS=

# The ')' must be on the line AFTER the EOF!
HELP_OPTIONS=$(cat <<EOF
  --config-file               Configuration file used to lauch applications
                              Default: ./app-install.properties
  --start-none                Run no app, but set up the tools and data files.
  --start-only X              Only start the following apps:
                                transform-data      Performs data ingestion & transformation
                                batch-k-means       Find the best K value.
                                anomaly-detection   Find anomalies in the data.
                              Repeat the option to run more than one.
                              Default: runs all of them. See also --start-none.
  --use-zeppelin              If specified, only data loader and transformer will be deployed.
                              Parameters that need to be set in Zeppelin notebooks are printed
                              to the console.
EOF
)

export run_transform_data=
export run_batch_k_means=
export run_anomaly_detection=
export zeppelin_flag_set=no
apps_selected=

config_file="$DIR/app-install.properties"

function create_topics {
  declare -a topics=(
    $KAFKA_FROM_TOPIC
    $KAFKA_TO_TOPIC
    $KAFKA_ERROR_TOPIC
    $KAFKA_CLUSTERS_TOPIC
    )
  for topic in "${topics[@]}"
  do
    $NOEXEC dcos $KAFKA_DCOS_PACKAGE topic create $topic --partitions $PARTITIONS --replication $REPLICATION_FACTOR --name "$KAFKA_DCOS_SERVICE_NAME"
    $NOEXEC add_to_json_array TOPICS $topic $APP_METADATA_FILE
  done
}

function generate_app_uninstall_metadata {
declare METADATA=$(cat <<EOF
{
  "ANOMALY_DETECTION_SPARK_DRIVER_SUBMIT_ID":"",
  "BATCH_KMEANS_SPARK_DRIVER_SUBMIT_ID":"",
  "TRANSFORM_DATA_APP_ID":"",
  "TOPICS": [ ],
  "KAFKA_DCOS_PACKAGE":"$KAFKA_DCOS_PACKAGE",
  "KAFKA_DCOS_SERVICE_NAME":"$KAFKA_DCOS_SERVICE_NAME"
}
EOF
)
  if [[ -z $NOEXEC ]]
  then
    echo "$METADATA" > $APP_METADATA_FILE
  else
    $NOEXEC "$METADATA > $APP_METADATA_FILE"
  fi
}

function modify_transform_data_template {
  cp $TRANSFORM_DATA_TEMPLATE_FILE $TRANSFORM_DATA_TEMPLATE
  declare -a arr=(
    "TRANSFORM_DATA_APP_ID"
    "TRANSFORM_DATA_IMAGE"
    "KAFKA_BROKERS"
    "KAFKA_FROM_TOPIC"
    "KAFKA_TO_TOPIC"
    "KAFKA_ERROR_TOPIC"
    "KAFKA_CLUSTERS_TOPIC"
    "KAFKA_ZOOKEEPER_URL"
    "NATIVE_PACKAGE_ON_MESOS"
    )

  for elem in "${arr[@]}"
  do
    eval value="\$$elem"
    $NOEXEC sed -i -- "s~{$elem}~\"$value\"~g" $TRANSFORM_DATA_TEMPLATE
  done

  ## without quotes substitution
  $NOEXEC sed -i -- "s~{VERSIONED_PROJECT_NAME}~$VERSIONED_PROJECT_NAME~g" $TRANSFORM_DATA_TEMPLATE
}

function load_transform_data_job {
  $NOEXEC dcos marathon app add $TRANSFORM_DATA_TEMPLATE
  $NOEXEC update_json_field TRANSFORM_DATA_APP_ID "$TRANSFORM_DATA_APP_ID" "$APP_METADATA_FILE"
}

function parse_arguments {

  while :; do
    case "$1" in
      --config-file)
      shift
      config_file=$1
      ;;
      --start-none)
      apps_selected=yes
      run_transform_data=
      run_batch_k_means=
      run_anomaly_detection=
      ;;
      --start*)
      apps_selected=yes
      shift
      case $1 in
        transform-data)     run_transform_data=yes     ;;
        batch-k-means)      run_batch_k_means=yes      ;;
        anomaly-detection)  run_anomaly_detection=yes  ;;
        *) error "Unrecognized value for --start-only: $1" ;;
      esac
      ;;
      --use-zeppelin)
      zeppelin_flag_set=yes
      run_transform_data=yes
      run_batch_k_means=yes
      run_anomaly_detection=yes
      ;;
      -h|--help)   # Call a "show_help" function to display a synopsis, then exit.
      show_help
      exit 0
      ;;
      -n|--no-exec)   # Don't actually run the installation commands; just print (for debugging)
      NOEXEC="echo running: "
      ;;
      --stop-at)      # for testing
        shift
        stop_point=$1
      ;;
      --)              # End of all options.
      shift
      break
      ;;
      '')              # End of all options.
      break
      ;;
      *)
      error "The option is not valid: $1"
      ;;
    esac
    shift
  done

  if [ -z "$apps_selected" ]
  then
    run_transform_data=yes
    run_batch_k_means=yes
    run_anomaly_detection=yes
  fi
}

function yes_or_no {
  if [ "$1" = "yes" ]
  then
    echo "yes"
  else
    echo "no"
  fi
}

keyval() {
  filename=$1
  if [ -f "$filename" ]
  then
    echo "$filename found"

    while IFS='=' read -r key value
    do
      if [ "$key" == "kafka-topic-partitions" ]
      then
        PARTITIONS=$value
      fi

      if [ "$key" == "kafka-topic-replication-factor" ]
      then
        REPLICATION_FACTOR=$value
      fi

      if [ "$key" == "kafka-dcos-package" ]
      then
        KAFKA_DCOS_PACKAGE=$value
      fi

      if [ "$key" == "kafka-dcos-service-name" ]
      then
        KAFKA_DCOS_SERVICE_NAME=$value
      fi

      if [ "$key" == "skip-create-topics" ]
      then
        SKIP_CREATE_TOPICS=$value
      fi

      if [ "$key" == "publish-user" ]
      then
        PUBLISH_USER=$value
      fi

      if [ "$key" == "publish-host" ]
      then
        PUBLISH_HOST=$value
      fi

      if [ "$key" == "ssh-port" ]
      then
        SSH_PORT=$value
      fi

      if [ "$key" == "passphrase" ]
      then
        SSH_PASSPHRASE=$value
      fi

      if [ "$key" == "ssh-keyfile" ]
      then
        SSH_KEYFILE=$value
      fi

      if [ "$key" == "laboratory-mesos-path" ]
      then
        LABORATORY_MESOS_PATH=$value
      fi

    done < "$filename"

    if [ -z "${DCOS_KAFKA_PACKAGE// }" ]
    then
      DCOS_KAFKA_PACKAGE=kafka
    fi
    if [ -z "${KAFKA_DCOS_SERVICE_NAME// }" ]
    then
      KAFKA_DCOS_SERVICE_NAME="${KAFKA_DCOS_PACKAGE}"
    fi
    if [ -z "${PARTITIONS// }" ]
    then
      PARTITIONS=1
    fi
    if [ -z "${REPLICATION_FACTOR// }" ]
    then
      REPLICATION_FACTOR=1
    fi
    if [ -z "${SKIP_CREATE_TOPICS// }" ]
    then
      SKIP_CREATE_TOPICS=false
    fi
    if [ -z "${PUBLISH_USER// }" ]
    then
      PUBLISH_USER="publisher"
    fi

    exit_if_not_defined_or_empty "$PUBLISH_HOST" "publish-host"
    exit_if_not_defined_or_empty "$SSH_PORT" "ssh-port"
    exit_if_not_defined_or_empty "$SSH_KEYFILE" "ssh-keyfile"
    exit_if_not_defined_or_empty "$LABORATORY_MESOS_PATH" "laboratory-mesos-path"

  else
    echo "$filename not found."
    exit 6
  fi
}

function exit_if_not_defined_or_empty() {
  value=$1
  name=$2

  if [ -z "${value// }"  ]
  then
    error "$name not defined .. exiting"
  fi
}

function generate_deploy_conf {
declare DEPLOY_CONF_DATA=$(cat <<EOF
{
  servers = [
   {
    name = "fdp-nwintrusion"
    user = $PUBLISH_USER
    host = $PUBLISH_HOST
    port = $SSH_PORT
    sshKeyFile = $SSH_KEYFILE
   }
  ]
}
EOF
)
  if [[ -z $NOEXEC ]]
  then
    echo "$DEPLOY_CONF_DATA" > "$DEPLOY_CONF_FILE"
  else
    $NOEXEC "$DEPLOY_CONF_DATA > $DEPLOY_CONF_FILE"
  fi
}

function build_app {
  $NOEXEC cd "$PROJ_ROOT_DIR"

  $NOEXEC sbt clean clean-files assembly
}

function deploy_app {
  $NOEXEC cd "$PROJ_ROOT_DIR"
  $NOEXEC sbt "deploySsh fdp-nwintrusion"

  if [[ -z $NOEXEC ]]
  then
    TGZ_NAME=$( ls "$PROJ_ROOT_DIR"/target/universal/*.tgz )
    VERSIONED_NATIVE_PACKAGE_NAME=$( basename "$TGZ_NAME" )

    VERSION=$(echo ${VERSIONED_NATIVE_PACKAGE_NAME%.*} | cut -d- -f4-)

    NATIVE_PACKAGE_ON_MESOS="$LABORATORY_MESOS_PATH"/"$VERSIONED_NATIVE_PACKAGE_NAME"
    VERSIONED_PROJECT_NAME="fdp-nw-intrusion-$VERSION"
    SPARK_APP_JAR="fdp-nw-intrusion-assembly-$VERSION.jar"
  fi
}

function load_transform_data_job {
  $NOEXEC dcos marathon app add "$TRANSFORM_DATA_TEMPLATE"
  $NOEXEC update_json_field TRANSFORM_DATA_APP_ID "$TRANSFORM_DATA_APP_ID" "$APP_METADATA_FILE"
}

function main {

  parse_arguments "$@"

  if [ ! -f $config_file ]
  then
    error "$config_file not found.."
  else
    keyval $config_file
  fi

  [ "$stop_point" = "config_file" ] && exit 0

  echo "Running:"
  echo "Ingest & Transform Data?    $(yes_or_no $run_transform_data)"
  echo "Batch K Means?              $(yes_or_no $run_batch_k_means)"
  echo "Anomaly Detection?          $(yes_or_no $run_anomaly_detection)"

  [ "$stop_point" = "start_only" ] && exit 0

  # remove metadata file
  $NOEXEC rm -f "$APP_METADATA_FILE"

  KAFKA_ZOOKEEPER_URL="master.mesos:$ZOOKEEPER_PORT/dcos-service-$KAFKA_DCOS_SERVICE_NAME"

  header "Verifying required tools are installed...\n"

  require_dcos_cli

  require_spark

  require_kafka

  require_jq

  require_templates

  header "Generating metadata for subsequent uninstalls...\n"

  generate_app_uninstall_metadata

  if [ "$SKIP_CREATE_TOPICS" = false ]; then
    header "Creating Kafka topics..."
    echo
    create_topics
  else
    echo "Skipped creating Kafka topics"
    # fill in topics we know
    declare -a arr=(
      "KAFKA_FROM_TOPIC"
      "KAFKA_TO_TOPIC"
      "KAFKA_ERROR_TOPIC"
      "KAFKA_CLUSTERS_TOPIC"
    )

    for elem in "${arr[@]}"
    do
      eval value="\$$elem"
      $NOEXEC add_to_json_array TOPICS $value $APP_METADATA_FILE
    done
  fi

  header Generating remote deployment information ..
  generate_deploy_conf

  header Building packages to be deployed ..
  build_app

  header "Gathering Kafka connection information...\n"
  gather_kafka_connection_info

  header Doing remote deployment ..
  deploy_app

  if [ -n "$run_transform_data" ]
  then
    header "Installing data transformation application... "
    echo
    modify_transform_data_template
    load_transform_data_job
  else
    echo "Skipped installing the data transformation application."
  fi

  if [ -n "$run_anomaly_detection" ]
  then
    header "Running the Spark application for anomaly detection... "
    echo
    run_anomaly_detection_spark_job \
      $DEFAULT_NO_OF_CLUSTERS \
      $DEFAULT_CLUSTERING_MICRO_BATCH_DURATION \
      $zeppelin_flag_set \
  else
    echo "Skipped running the Spark application for anomaly detection."
  fi

  if [ -n "$run_batch_k_means" ]
  then
    header "Running the Spark application for optimizing K for K-Means... "
    echo
    run_batch_kmeans_spark_job \
      $DEFAULT_OPTIMAL_K_FROM_CLUSTER_COUNT \
      $DEFAULT_OPTIMAL_K_TO_CLUSTER_COUNT \
      $DEFAULT_OPTIMAL_K_INCREMENT \
      $DEFAULT_OPTIMAL_K_CLUSTERING_MICRO_BATCH_DURATION \
      $zeppelin_flag_set \
  else
    echo "Skipped running the Spark application for optimizing K for K-Means."
  fi

  echo
  echo "Network Intrusion application was successfully installed!"
}

main "$@"
exit 0
