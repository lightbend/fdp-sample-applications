#!/usr/bin/env bash
set -e
# set -x

SCRIPT=$(basename "${BASH_SOURCE[0]}")

## run directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../bin/common.sh"

## project root directory
PROJ_ROOT_DIR="$( cd "$DIR/../source/core" && pwd -P )"

## deploy.conf full path
DEPLOY_CONF_FILE="$PROJ_ROOT_DIR/deploy.conf"

ZOOKEEPER_PORT=2181

VERSIONED_PROJECT_NAME=
VERSIONED_NATIVE_PACKAGE_NAME=
TRAVEL_TIME_APP_JAR=
TRAVEL_TIME_APP_JAR_ON_MESOS=

# Used by show_help
HELP_MESSAGE="Installs the NYC Taxi Ride sample app. Assumes DC/OS authentication was successful
  using the DC/OS CLI."
HELP_EXAMPLE_OPTIONS=

# The ')' must be on the line AFTER the EOF!
HELP_OPTIONS=$(cat <<EOF
  --config-file               Configuration file used to lauch applications
                              Default: ./app-install.properties
  --start-none                Run no app, but set up the tools and data files.
  --start-only X              Only start the following apps:
                                ingestion      Performs data ingestion & transformation
                                app            Deploys the taxi travel time prediction app
                              Repeat the option to run more than one.
                              Default: runs all of them. See also --start-none.
EOF
)

export run_ingestion=
export run_app=
apps_selected=

config_file="$DIR/app-install.properties"

INGESTION_TEMPLATE_FILE="$DIR/ingestion.json.template"
INGESTION_TEMPLATE=${INGESTION_TEMPLATE_FILE%.*}
INGESTION_DATA_APP_ID="nyctaxi-load-data"

APP_TEMPLATE_FILE="$DIR/taxiride.json.template"
APP_TEMPLATE=${APP_TEMPLATE_FILE%.*}
APP_DATA_APP_ID="nyctaxi-app"

KAFKA_IN_TOPIC="taxiin"
KAFKA_OUT_TOPIC="taxiout"

function create_topics {
  declare -a topics=(
    $KAFKA_IN_TOPIC
    $KAFKA_OUT_TOPIC
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
  "INGESTION_DATA_APP_ID":"",
  "APP_DATA_APP_ID":"",
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

function modify_ingestion_data_template {
  cp $INGESTION_TEMPLATE_FILE $INGESTION_TEMPLATE
  declare -a arr=(
    "INGESTION_DATA_APP_ID"
    "KAFKA_BROKERS"
    "KAFKA_IN_TOPIC"
    "KAFKA_OUT_TOPIC"
    "KAFKA_ZOOKEEPER_URL"
    "NATIVE_PACKAGE_ON_MESOS"
    )

  for elem in "${arr[@]}"
  do
    eval value="\$$elem"
    $NOEXEC sed -i -- "s~{$elem}~\"$value\"~g" $INGESTION_TEMPLATE
  done

  ## without quotes substitution
  $NOEXEC sed -i -- "s~{VERSIONED_PROJECT_NAME}~$VERSIONED_PROJECT_NAME~g" $INGESTION_TEMPLATE
}

function load_ingestion_data_job {
  $NOEXEC dcos marathon app add $INGESTION_TEMPLATE
  $NOEXEC update_json_field INGESTION_DATA_APP_ID "$INGESTION_DATA_APP_ID" "$APP_METADATA_FILE"
}

function modify_app_template {
  cp $APP_TEMPLATE_FILE $APP_TEMPLATE
  declare -a arr=(
    "APP_DATA_APP_ID"
    "TRAVEL_TIME_APP_JAR_ON_MESOS"
    )

  for elem in "${arr[@]}"
  do
    eval value="\$$elem"
    $NOEXEC sed -i -- "s~{$elem}~\"$value\"~g" $APP_TEMPLATE
  done

  ## without quotes substitution
  declare -a arr2=(
    "KAFKA_BROKERS"
    "KAFKA_IN_TOPIC"
    "KAFKA_OUT_TOPIC"
    "JM_RPC_ADDRESS"
    "JM_RPC_PORT"
    "TRAVEL_TIME_APP_JAR"
    )

  for elem in "${arr2[@]}"
  do
    eval value="\$$elem"
    $NOEXEC sed -i -- "s~{$elem}~$value~g" $APP_TEMPLATE
  done
}

function load_app_job {
  $NOEXEC dcos marathon app add $APP_TEMPLATE
  $NOEXEC update_json_field APP_DATA_APP_ID "$APP_DATA_APP_ID" "$APP_METADATA_FILE"
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
      run_ingestion=
      run_app=
      ;;
      --start*)
      apps_selected=yes
      shift
      case $1 in
        ingestion)     run_ingestion=yes     ;;
        app)           run_app=yes      ;;
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
    run_ingestion=yes
    run_app=yes
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
    name = "fdp-flink-taxiride-ingestion"
    user = $PUBLISH_USER
    host = $PUBLISH_HOST
    port = $SSH_PORT
    sshKeyFile = $SSH_KEYFILE
   },
   {
    name = "fdp-flink-taxiride-app"
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

  if [ -n "$run_ingestion" ]
  then
    $NOEXEC sbt ingestion/clean ingestion/clean-files ingestion/universal:packageZipTarball
  fi
  if [ -n "$run_app" ]
  then
    $NOEXEC sbt app/clean app/clean-files app/assembly
  fi
}

function deploy_app {
  $NOEXEC cd "$PROJ_ROOT_DIR"

  if [ -n "$run_ingestion" ]
  then
    $NOEXEC sbt ingestion/clean ingestion/cleanFiles "ingestion/deploySsh fdp-flink-taxiride-ingestion"
    if [[ -z $NOEXEC ]]
    then
      TGZ_NAME=$( ls "$PROJ_ROOT_DIR"/ingestion/target/universal/*.tgz )
      VERSIONED_NATIVE_PACKAGE_NAME=$( basename "$TGZ_NAME" )

      VERSION=$(echo ${VERSIONED_NATIVE_PACKAGE_NAME%.*} | cut -d- -f2-)

      NATIVE_PACKAGE_ON_MESOS="$LABORATORY_MESOS_PATH"/"$VERSIONED_NATIVE_PACKAGE_NAME"
      VERSIONED_PROJECT_NAME="ingestion-$VERSION"
    fi
  fi

  if [ -n "$run_app" ]
  then
    $NOEXEC sbt app/clean app/cleanFiles "app/deploySsh fdp-flink-taxiride-app"
    if [[ -z $NOEXEC ]]
    then
      FULL_JAR_NAME=$( ls "$PROJ_ROOT_DIR"/app/target/scala*/*.jar )
      TRAVEL_TIME_APP_JAR=$( basename "$FULL_JAR_NAME" )
      TRAVEL_TIME_APP_JAR_ON_MESOS="$LABORATORY_MESOS_PATH/$TRAVEL_TIME_APP_JAR"
    fi
  fi

}

function require_templates {
  # check if template files are available
  if [ ! -f  "$INGESTION_TEMPLATE_FILE" ]; then
    msg=("$INGESTION_TEMPLATE_FILE is missing or is not a file."
         "Please copy the file in $DIR")
    error "${msg[@]}"
  fi

  if [ ! -f  "$APP_TEMPLATE_FILE" ]; then
    msg=("$APP_TEMPLATE_FILE is missing or is not a file."
         "Please copy the file in $DIR")
    error "${msg[@]}"
  fi
}

function set_job_manager_info {
  # get the jobmanager ip and port
  curl -s --header "Authorization: token=$(dcos config show core.dcos_acs_token)" $(dcos config show \
  | grep  core.dcos_url | awk '{print $2}')/service/flink/jobmanager/config/ > $DIR/jobmanager.conf

  JM_RPC_ADDRESS=$(cat $DIR/jobmanager.conf | jq . | grep -n1 jobmanager.rpc.address \
  | grep value | awk '{print $3}')

  JM_RPC_PORT=$(cat $DIR/jobmanager.conf | jq . | grep -n1 jobmanager.rpc.port \
  | grep value | awk '{print $3}')

  JM_RPC_ADDRESS=$(remove_quotes $JM_RPC_ADDRESS)

  JM_RPC_PORT=$(remove_quotes $JM_RPC_PORT)
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
  echo "Ingest Data?              $(yes_or_no $run_ingestion)"
  echo "Travel time prediction?   $(yes_or_no $run_app)"

  [ "$stop_point" = "start_only" ] && exit 0

  # remove metadata file
  $NOEXEC rm -f "$APP_METADATA_FILE"

  KAFKA_ZOOKEEPER_URL="master.mesos:$ZOOKEEPER_PORT/dcos-service-$KAFKA_DCOS_SERVICE_NAME"

  header "Verifying required tools are installed...\n"

  require_dcos_cli

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
      "KAFKA_IN_TOPIC"
      "KAFKA_OUT_TOPIC"
    )

    for elem in "${arr[@]}"
    do
      eval value="\$$elem"
      $NOEXEC add_to_json_array TOPICS $value $APP_METADATA_FILE
    done
  fi

  header Generating remote deployment information ..
  generate_deploy_conf

  header Getting Flink Job Manager information
  set_job_manager_info

  header Building packages to be deployed ..
# build_app

  header "Gathering Kafka connection information...\n"
  gather_kafka_connection_info

  header Doing remote deployment ..
  deploy_app

  if [ -n "$run_ingestion" ]
  then
    header "Installing data transformation application... "
    echo
    modify_ingestion_data_template
    load_ingestion_data_job
  else
    echo "Skipped installing the data transformation application."
  fi

  if [ -n "$run_app" ]
  then
    header "Installing travel time prediction application... "
    echo
    modify_app_template
    load_app_job
  else
    echo "Skipped installing the travel time prediction application."
  fi

  echo
  echo "NYC Taxiride Travel Time Prediction application was successfully installed!"
}

main "$@"
exit 0

