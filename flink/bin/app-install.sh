#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../bin/common.sh"

# Used by show_help
HELP_MESSAGE="Installs the NYC Taxi Ride sample app. Assumes DC/OS authentication was successful
  using the DC/OS CLI."
HELP_EXAMPLE_OPTIONS=

# The ')' must be on the line AFTER the EOF!
HELP_OPTIONS=$(cat <<EOF
  --config-file               Configuration file used to lauch applications
                              Default: ./app-install.properties
EOF
)

LOAD_DATA_TEMPLATE_FILE="$DIR/load-data.json.template"
LOAD_DATA_TEMPLATE=${LOAD_DATA_TEMPLATE_FILE%.*}
LOAD_DATA_APP_ID="nyctaxi-load-data"

KAFKA_FROM_TOPIC="taxiin"
KAFKA_TO_TOPIC="taxiout"
ZOOKEEPER_PORT=2181

config_file="./app-install.properties"

function create_topics {
  declare -a topics=(
    $KAFKA_FROM_TOPIC
    $KAFKA_TO_TOPIC
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
  "LOAD_DATA_APP_ID":"",
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

function modify_data_loader_template {
  cp $LOAD_DATA_TEMPLATE_FILE $LOAD_DATA_TEMPLATE
  declare -a arr=(
    "LOAD_DATA_APP_ID"
    "LOAD_DATA_IMAGE"
    "AWS_ACCESS_KEY_ID"
    "AWS_SECRET_ACCESS_KEY"
    "KAFKA_BROKERS"
    "KAFKA_FROM_TOPIC"
    "KAFKA_TO_TOPIC"
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

function load_data_loader_job {
  $NOEXEC dcos marathon app add $LOAD_DATA_TEMPLATE
  $NOEXEC update_json_field LOAD_DATA_APP_ID "$LOAD_DATA_APP_ID" "$APP_METADATA_FILE"
}

function parse_arguments {

  while :; do
    case "$1" in
      --config-file)
      shift
      config_file=$1
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

function require_templates {
  # check if load data template file is available
  if [ ! -f  "$LOAD_DATA_TEMPLATE_FILE" ]; then
    msg=("$LOAD_DATA_TEMPLATE_FILE is missing or is not a file."
         "Please copy the file in $DIR or use package.sh to properly setup this app.")
    error "${msg[@]}"
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

  # remove metadata file
  $NOEXEC rm -f "$APP_METADATA_FILE"

  KAFKA_ZOOKEEPER_URL="master.mesos:$ZOOKEEPER_PORT/dcos-service-$KAFKA_DCOS_PACKAGE"

  LOAD_DATA_IMAGE="$DOCKER_USERNAME/fdp-nyc-taxiride-load-data"

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
    )

    for elem in "${arr[@]}"
    do
      eval value="\$$elem"
      $NOEXEC add_to_json_array TOPICS $value $APP_METADATA_FILE
    done
  fi

  header "Gathering Kafka connection information...\n"

  gather_kafka_connection_info

  header "Running the data loading application... "
  modify_data_loader_template
  load_data_loader_job

  echo
  echo "NYC Taxi Ride sample application was successfully installed!"
}

main "$@"
exit 0

