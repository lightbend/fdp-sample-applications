#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/utils.sh"

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
                                data-loader         Loads data from S3
                                transform-data      Performs initial transformation
                                batch-k-means       Find the best K value.
                                anomaly-detection   Find anomalies in the data.
                                visualizer          Run the Jupyter-based visualization.
                              Repeat the option to run more than one.
                              Default: runs all of them. See also --start-none.
EOF
)


export run_data_loader=
export run_transform_data=
export run_batch_k_means=
export run_anomaly_detection=
export run_visualizer=
apps_selected=
config_file="./app-install.properties"


function create_topics {
  declare -a topics=(
    $KAFKA_FROM_TOPIC
    $KAFKA_TO_TOPIC
    $KAFKA_ERROR_TOPIC
    $KAFKA_CLUSTERS_TOPIC
    )
  for topic in "${topics[@]}"
  do
    $NOEXEC dcos $KAFKA_DCOS_PACKAGE topic create $topic --partitions $PARTITIONS --replication $REPLICATION_FACTOR
    $NOEXEC add_to_json_array TOPICS $topic $APP_METADATA_FILE
  done
}

function generate_app_uninstall_metadata {
declare METADATA=$(cat <<EOF
{
  "ANOMALY_DETECTION_SPARK_DRIVER_SUBMIT_ID":"",
  "BATCH_KMEANS_SPARK_DRIVER_SUBMIT_ID":"",
  "TRANSFORM_DATA_APP_ID":"",
  "LOAD_DATA_APP_ID":"",
  "VIS_DATA_APP_ID":"",
  "TOPICS": [ ],
  "KAFKA_DCOS_PACKAGE":"$KAFKA_DCOS_PACKAGE"
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
    )

  for elem in "${arr[@]}"
  do
    eval value="\$$elem"
    $NOEXEC sed -i -- "s~{$elem}~\"$value\"~g" $TRANSFORM_DATA_TEMPLATE
  done
}

function modify_data_loader_template {
  cp $LOAD_DATA_TEMPLATE_FILE $LOAD_DATA_TEMPLATE
  declare -a arr=(
    "LOAD_DATA_APP_ID"
    "LOAD_DATA_IMAGE"
    "AWS_ACCESS_KEY_ID"
    "AWS_SECRET_ACCESS_KEY"
    "KAFKA_BROKERS"
    "KAFKA_FROM_TOPIC"
    "KAFKA_ZOOKEEPER_URL"
    "S3_BUCKET_URL"
    "WITH_IAM_ROLE"
    )

  for elem in "${arr[@]}"
  do
    eval value="\$$elem"
    $NOEXEC sed -i -- "s~{$elem}~\"$value\"~g" $LOAD_DATA_TEMPLATE
  done
}

function modify_vis_data_template {
  cp $VIS_DATA_TEMPLATE_FILE $VIS_DATA_TEMPLATE
  declare -a arr=(
    "VIS_DATA_APP_ID"
    "VIS_DATA_IMAGE"
    )

  for elem in "${arr[@]}"
  do
    eval value="\$$elem"
    $NOEXEC sed -i -- "s~{$elem}~\"$value\"~g" $VIS_DATA_TEMPLATE
  done
}

function load_transform_data_job {
  $NOEXEC dcos marathon app add $TRANSFORM_DATA_TEMPLATE
  $NOEXEC update_json_field TRANSFORM_DATA_APP_ID "$TRANSFORM_DATA_APP_ID" "$APP_METADATA_FILE"
}

function load_data_loader_job {
  $NOEXEC dcos marathon app add $LOAD_DATA_TEMPLATE
  $NOEXEC update_json_field LOAD_DATA_APP_ID "$LOAD_DATA_APP_ID" "$APP_METADATA_FILE"
}

function load_visualize_data_job {
  $NOEXEC dcos marathon app add $VIS_DATA_TEMPLATE
  $NOEXEC update_json_field VIS_DATA_APP_ID "$VIS_DATA_APP_ID" "$APP_METADATA_FILE"
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
      run_visualizer=
      ;;
      --start*)
      apps_selected=yes
      shift
      case $1 in
        data-loader)        run_data_loader=yes        ;;
        transform-data)     run_transform_data=yes     ;;
        batch-k-means)      run_batch_k_means=yes      ;;
        anomaly-detection)  run_anomaly_detection=yes  ;;
        visualizer)         run_visualizer=yes         ;;
        *) error "Unrecognized value for --start-only: $1" ;;
      esac
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
    run_data_loader=yes
    run_transform_data=yes
    run_batch_k_means=yes
    run_anomaly_detection=yes
    run_visualizer=yes
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
      if [ "$key" == "docker-username" ]
      then
        DOCKER_USERNAME=$value
      fi

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

      if [ "$key" == "skip-create-topics" ]
      then
        SKIP_CREATE_TOPICS=$value
      fi

      if [ "$key" == "s3-bucket-url" ]
      then
        S3_BUCKET_URL=$value
      fi

      if [ "$key" == "with-iam-role" ]
      then
	if [ "$value" != true ]
	then
	  WITH_IAM_ROLE=
        else
          WITH_IAM_ROLE=$value
        fi
      fi
    done < "$filename"

    if [ -z $DOCKER_USERNAME ]
    then
      error 'docker-username requires a non-empty argument.'
    fi
    if [ -z $DCOS_KAFKA_PACKAGE ]
    then
      DCOS_KAFKA_PACKAGE=kafka
    fi
    if [ -z $PARTITIONS ]
    then
      PARTITIONS=1
    fi
    if [ -z $REPLICATION_FACTOR ]
    then
      REPLICATION_FACTOR=1
    fi
    if [ -z $SKIP_CREATE_TOPICS ]
    then
      SKIP_CREATE_TOPICS=false
    fi
    if [ -z $S3_BUCKET_URL ]
    then
      error 's3-bucket-url requires a non-empty argument.'
    fi
  else
    echo "$filename not found."
    exit 6
  fi
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
  echo "Data Loader?       $(yes_or_no $run_data_loader)"
  echo "Transform Data?    $(yes_or_no $run_transform_data)"
  echo "Batch K Means?     $(yes_or_no $run_batch_k_means)"
  echo "Anomaly Detection? $(yes_or_no $run_anomaly_detection)"
  echo "Visualizer?        $(yes_or_no $run_visualizer)"

  [ "$stop_point" = "start_only" ] && exit 0

  # remove metadata file
  $NOEXEC rm -f "$APP_METADATA_FILE"

  KAFKA_ZOOKEEPER_URL="master.mesos:$ZOOKEEPER_PORT/dcos-service-$KAFKA_DCOS_PACKAGE"

  TRANSFORM_DATA_IMAGE="$DOCKER_USERNAME/fdp-nw-intrusion-transform-data"
  LOAD_DATA_IMAGE="$DOCKER_USERNAME/fdp-nw-intrusion-load-data"
  VIS_DATA_IMAGE="$DOCKER_USERNAME/fdp-nw-intrusion-visualize-data"

  header "Verifying required tools are installed...\n"

  require_dcos_cli

  require_spark

  require_kafka

  require_jq

  require_templates

  header "Generating metadata for subsequent uninstalls...\n"

  generate_app_uninstall_metadata

  header "Creating Kafka topics..."

  if [ "$SKIP_CREATE_TOPICS" = false ]; then
    echo
    create_topics
  else
    echo "skipped"
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

  header "Gathering Kafka connection information...\n"

  gather_kafka_connection_info

  header "Installing data transformation application... "
  if [ -n "$run_transform_data" ]
  then
    echo
    modify_transform_data_template
    load_transform_data_job
  else
    echo "skipped"
  fi

  header "Running the spark application for anomaly detection... "
  if [ -n "$run_anomaly_detection" ]
  then
    echo
    echo "  k = $DEFAULT_NO_OF_CLUSTERS"
    echo "  micro batch duration = $DEFAULT_CLUSTERING_MICRO_BATCH_DURATION seconds"
    run_anomaly_detection_spark_job \
      $DEFAULT_NO_OF_CLUSTERS \
      $DEFAULT_CLUSTERING_MICRO_BATCH_DURATION \
      $S3_BUCKET_URL
  else
    echo "skipped"
  fi

  header "Running the spark application for optimizing K for K-Means... "
  if [ -n "$run_batch_k_means" ]
  then
    echo
    echo "  micro batch duration = $DEFAULT_OPTIMAL_K_CLUSTERING_MICRO_BATCH_DURATION"
    echo "  Trying K between $DEFAULT_OPTIMAL_K_FROM_CLUSTER_COUNT and $DEFAULT_OPTIMAL_K_TO_CLUSTER_COUNT (inclusive)"
    echo "  Delta between K values = $DEFAULT_OPTIMAL_K_INCREMENT"
    run_batch_kmeans_spark_job \
      $DEFAULT_OPTIMAL_K_FROM_CLUSTER_COUNT \
      $DEFAULT_OPTIMAL_K_TO_CLUSTER_COUNT \
      $DEFAULT_OPTIMAL_K_INCREMENT \
      $DEFAULT_OPTIMAL_K_CLUSTERING_MICRO_BATCH_DURATION \
      $S3_BUCKET_URL
  else
    echo "skipped"
  fi

  header "Running the data loading application... "
  if [ -n "$run_data_loader" ]
  then
    echo
    modify_data_loader_template
    load_data_loader_job
  else
    echo "skipped"
  fi

  header "Running the data visualization application... "
  if [ -n "$run_visualizer" ]
  then
    echo
    modify_vis_data_template
    load_visualize_data_job
  else
    echo "skipped"
  fi

  echo
  echo "Network Intrusion application was successfully installed!"
}

main "$@"
exit 0
