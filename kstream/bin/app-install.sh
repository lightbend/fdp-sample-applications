#!/usr/bin/env bash
set -e
# set -x

SCRIPT=$(basename "${BASH_SOURCE[0]}")

## run directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

## project root directory
PROJ_ROOT_DIR="$( cd "$DIR/../source/core" && pwd -P )"

. "$DIR/../../version.sh"
. "$DIR/../../bin/common.sh"

ZOOKEEPER_PORT=2181
APP_METADATA_FILE_DSL="$DIR/app.metadata.dsl.json"
APP_METADATA_FILE_PROC="$DIR/app.metadata.proc.json"

# Used by show_help
HELP_MESSAGE="Installs the Kafka Streams sample application. Assumes DC/OS authentication was successful
  using the DC/OS CLI."
HELP_EXAMPLE_OPTIONS=

HELP_OPTIONS=$(cat <<EOF
  --config-file               Configuration file used to launch applications
                              Default: ./app-install.properties
  --start-only X              Only start the following apps:
                                dsl         Starts topology based on Kafka Streams DSL
                                processor   Starts topology that implements custom state repository based on Kafka Streams Processor APIs
                              Repeat the option to run more than one.
                              Default: runs all of them
EOF
)

config_file="$DIR/app-install.properties"

## kafka topics for the dsl module
KAFKA_FROM_TOPIC_DSL=${KAFKA_FROM_TOPIC_DSL:-server-log-dsl}
KAFKA_TO_TOPIC_DSL=${KAFKA_TO_TOPIC_DSL:-processed-log}
KAFKA_AVRO_TOPIC_DSL=${KAFKA_AVRO_TOPIC_DSL:-avro-topic}
KAFKA_SUMMARY_ACCESS_TOPIC_DSL=${KAFKA_SUMMARY_ACCESS_TOPIC_DSL:-summary-access-log}
KAFKA_WINDOWED_SUMMARY_ACCESS_TOPIC_DSL=${KAFKA_WINDOWED_SUMMARY_ACCESS_TOPIC_DSL:-windowed-summary-access-log}
KAFKA_SUMMARY_PAYLOAD_TOPIC_DSL=${KAFKA_SUMMARY_PAYLOAD_TOPIC_DSL:-summary-payload-log}
KAFKA_WINDOWED_SUMMARY_PAYLOAD_TOPIC_DSL=${KAFKA_WINDOWED_SUMMARY_PAYLOAD_TOPIC_DSL:-windowed-summary-payload-log}
KAFKA_ERROR_TOPIC_DSL=${KAFKA_ERROR_TOPIC_DSL:-logerr-dsl}

## kafka topics for the proc module
KAFKA_FROM_TOPIC_PROC=${KAFKA_FROM_TOPIC_PROC:-server-log-proc}
KAFKA_ERROR_TOPIC_PROC=${KAFKA_ERROR_TOPIC_PROC:-logerr-proc}

## template files
KSTREAM_DSL_JSON_TEMPLATE="$DIR/kstream-app-dsl.json.template"
KSTREAM_DSL_JSON=${KSTREAM_DSL_JSON_TEMPLATE%.*}
KSTREAM_PROC_JSON_TEMPLATE="$DIR/kstream-app-proc.json.template"
KSTREAM_PROC_JSON=${KSTREAM_PROC_JSON_TEMPLATE%.*}

export run_dsl=
export run_proc=
apps_selected=

function create_topics {
  if [ -n "$run_dsl" ]
  then
    echo "Creating Kafka topics for DSL based module .."
    declare -a topics=(
      $KAFKA_FROM_TOPIC_DSL
      $KAFKA_TO_TOPIC_DSL
      $KAFKA_AVRO_TOPIC_DSL
      $KAFKA_SUMMARY_ACCESS_TOPIC_DSL
      $KAFKA_WINDOWED_SUMMARY_ACCESS_TOPIC_DSL
      $KAFKA_SUMMARY_PAYLOAD_TOPIC_DSL
      $KAFKA_WINDOWED_SUMMARY_PAYLOAD_TOPIC_DSL
      $KAFKA_ERROR_TOPIC_DSL
      )
    for topic in "${topics[@]}"
    do
      $NOEXEC dcos "$KAFKA_DCOS_PACKAGE" topic create "$topic" --partitions "$PARTITIONS" --replication "$REPLICATION_FACTOR" --name "$KAFKA_DCOS_SERVICE_NAME"
      $NOEXEC add_to_json_array TOPICS $topic $APP_METADATA_FILE_DSL
    done
  fi

  if [ -n "$run_proc" ]
  then
    echo "Creating Kafka topics for Processor based module .."
    declare -a topics=(
      $KAFKA_FROM_TOPIC_PROC
      $KAFKA_ERROR_TOPIC_PROC
      )
    for topic in "${topics[@]}"
    do
      $NOEXEC dcos "$KAFKA_DCOS_PACKAGE" topic create "$topic" --partitions "$PARTITIONS" --replication "$REPLICATION_FACTOR" --name "$KAFKA_DCOS_SERVICE_NAME"
      $NOEXEC add_to_json_array TOPICS $topic $APP_METADATA_FILE_PROC
    done
  fi
}

function generate_app_uninstall_metadata_dsl {
declare METADATA=$(cat <<EOF
{
  "KSTREAM_DSL_APP_ID":"",
  "TOPICS": [ ],
  "KAFKA_DCOS_PACKAGE":"$KAFKA_DCOS_PACKAGE",
  "KAFKA_DCOS_SERVICE_NAME":"$KAFKA_DCOS_SERVICE_NAME"
}
EOF
)
  if [[ -z $NOEXEC ]]
  then
    echo "$METADATA" > $APP_METADATA_FILE_DSL
  else
    $NOEXEC "$METADATA > $APP_METADATA_FILE_DSL"
  fi
}

function generate_app_uninstall_metadata_proc {
declare METADATA=$(cat <<EOF
{
  "KSTREAM_PROC_APP_ID":"",
  "TOPICS": [ ],
  "KAFKA_DCOS_PACKAGE":"$KAFKA_DCOS_PACKAGE",
  "KAFKA_DCOS_SERVICE_NAME":"$KAFKA_DCOS_SERVICE_NAME"
}
EOF
)
  if [[ -z $NOEXEC ]]
  then
    echo "$METADATA" > $APP_METADATA_FILE_PROC
  else
    $NOEXEC "$METADATA > $APP_METADATA_FILE_PROC"
  fi
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
      --start*)
      apps_selected=yes
      shift
      case $1 in
        dsl)        run_dsl=yes   ;;
        processor)  run_proc=yes  ;;
        *) error "Unrecognized value for --start-only: $1" ;;
      esac
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
    run_dsl=yes
    run_proc=yes
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

    done < "$filename"

    if [ -z "${KAFKA_DCOS_PACKAGE// }" ]
    then
      KAFKA_DCOS_PACKAGE=kafka
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

    set_schema_registry_url

  else
    echo "$filename not found."
    exit 6
  fi
}

function set_schema_registry_url() {
  local str=$( $NOEXEC dcos marathon task list --json /schema-registry | $NOEXEC jq 'select(length > 0) | .[0] | {host:.host, port:.ports[0]}' )
  if [ -z "$str" ]
  then
    SCHEMA_REGISTRY_URL=
  else
    local host=$( $NOEXEC echo "$str" | $NOEXEC jq '.host' )
    local port=$( $NOEXEC echo "$str" | $NOEXEC jq '.port' )

    SCHEMA_REGISTRY_URL="http://${host//\"}:$port"
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

function modify_dsl_json_template {
  $NOEXEC cp "$KSTREAM_DSL_JSON_TEMPLATE" "$KSTREAM_DSL_JSON"

  declare -a arr=(
    "KAFKA_BROKERS"
    "KAFKA_FROM_TOPIC_DSL"
    "KAFKA_TO_TOPIC_DSL"
    "KAFKA_AVRO_TOPIC_DSL"
    "KAFKA_SUMMARY_ACCESS_TOPIC_DSL"
    "KAFKA_WINDOWED_SUMMARY_ACCESS_TOPIC_DSL"
    "KAFKA_SUMMARY_PAYLOAD_TOPIC_DSL"
    "KAFKA_WINDOWED_SUMMARY_PAYLOAD_TOPIC_DSL"
    "KAFKA_ERROR_TOPIC_DSL"
    "SCHEMA_REGISTRY_URL"
    "VERSION"
    )

  for elem in "${arr[@]}"
  do
    eval value="\$$elem"
    $NOEXEC sed -i -- "s~{$elem}~\"$value\"~g" "$KSTREAM_DSL_JSON"
  done
}

function modify_proc_json_template {
  $NOEXEC cp "$KSTREAM_PROC_JSON_TEMPLATE" "$KSTREAM_PROC_JSON"

  declare -a arr=(
    "KAFKA_BROKERS"
    "KAFKA_FROM_TOPIC_PROC"
    "KAFKA_ERROR_TOPIC_PROC"
    "VERSION"
    )

  for elem in "${arr[@]}"
  do
    eval value="\$$elem"
    $NOEXEC sed -i -- "s~{$elem}~\"${value//\"}\"~g" "$KSTREAM_PROC_JSON"
  done
}

function load_marathon_job {
  if [ -n "$run_dsl" ]
  then
    $NOEXEC dcos marathon app add "$KSTREAM_DSL_JSON"
    $NOEXEC update_json_field KSTREAM_DSL_APP_ID "kstream-app-dsl" "$APP_METADATA_FILE_DSL"
  fi

  if [ -n "$run_proc" ]
  then
    $NOEXEC dcos marathon app add "$KSTREAM_PROC_JSON"
    $NOEXEC update_json_field KSTREAM_PROC_APP_ID "kstream-app-proc" "$APP_METADATA_FILE_PROC"
  fi
}

function require_templates {
  # check if transform data file is available
  if [ ! -f  "$KSTREAM_DSL_JSON_TEMPLATE" ]; then
    msg=("$KSTREAM_DSL_JSON_TEMPLATE is missing or is not a file.")
    error "${msg[@]}"
  fi

  # check if load data template file is available
  if [ ! -f  "$KSTREAM_PROC_JSON_TEMPLATE" ]; then
    msg=("$KSTREAM_PROC_JSON_TEMPLATE is missing or is not a file.")
    error "${msg[@]}"
  fi
}


function main {

  parse_arguments "$@"

  if [ ! -f "$config_file" ]
  then
    error "$config_file not found.."
  else
    keyval "$config_file"
  fi

  [ "$stop_point" = "config_file" ] && exit 0

  echo "Running:"
  echo "DSL based?          $(yes_or_no $run_dsl)"
  echo "Processor based?    $(yes_or_no $run_proc)"

  [ "$stop_point" = "start_only" ] && exit 0

  KAFKA_ZOOKEEPER_URL="master.mesos:$ZOOKEEPER_PORT/dcos-service-$KAFKA_DCOS_SERVICE_NAME"

  header "Verifying required tools are installed...\n"

  require_dcos_cli
  require_kafka
  require_jq
  require_templates

  header "Generating metadata for subsequent uninstalls...\n"

  if [ -n "$run_dsl" ]
  then
    # remove metadata file
    $NOEXEC rm -f "$APP_METADATA_FILE_DSL"
    generate_app_uninstall_metadata_dsl
  fi

  if [ -n "$run_proc" ]
  then
    # remove metadata file
    $NOEXEC rm -f "$APP_METADATA_FILE_PROC"
    generate_app_uninstall_metadata_proc
  fi

  if [ "$SKIP_CREATE_TOPICS" = false ]; then
    header "Creating Kafka topics..."
    echo
    create_topics
  else
    echo "Skipped creating Kafka topics"

    ## generate uninstall information for topic delete
    if [ -n "$run_dsl" ]
    then
      # fill in topics we know
      declare -a topics=(
        $KAFKA_FROM_TOPIC_DSL
        $KAFKA_TO_TOPIC_DSL
        $KAFKA_AVRO_TOPIC_DSL
        $KAFKA_SUMMARY_ACCESS_TOPIC_DSL
        $KAFKA_WINDOWED_SUMMARY_ACCESS_TOPIC_DSL
        $KAFKA_SUMMARY_PAYLOAD_TOPIC_DSL
        $KAFKA_WINDOWED_SUMMARY_PAYLOAD_TOPIC_DSL
        $KAFKA_ERROR_TOPIC_DSL
        )
      for topic in "${topics[@]}"
      do
        $NOEXEC add_to_json_array TOPICS $topic $APP_METADATA_FILE_DSL
      done
    fi
    if [ -n "$run_proc" ]
    then
      # fill in topics we know
      declare -a topics=(
        $KAFKA_FROM_TOPIC_PROC
        $KAFKA_ERROR_TOPIC_PROC
        )
      for topic in "${topics[@]}"
      do
        $NOEXEC add_to_json_array TOPICS $topic $APP_METADATA_FILE_PROC
      done
    fi
  fi

  header "Gathering Kafka connection information...\n"
  gather_kafka_connection_info

  header Generating information for Marathon service ..
  if [ -n "$run_dsl" ]
  then
    modify_dsl_json_template
  fi

  if [ -n "$run_proc" ]
  then
    modify_proc_json_template
  fi

  [ "$stop_point" = "marathon_json" ] && exit 0

  echo Loading Marathon Jobs ..
  load_marathon_job

  echo
  echo "Kafka Streams application was successfully installed!"
  echo
}

main "$@"
exit 0

