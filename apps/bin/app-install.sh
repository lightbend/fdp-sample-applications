#!/usr/bin/env bash
set -e
# set -x

SCRIPT=$(basename "${BASH_SOURCE[0]}")

## run directory
RELDIR=$(dirname ${BASH_SOURCE[0]})
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../version.sh"
. "$DIR/common.sh"

DEF_CONFIG_FILE="$RELDIR/config.json"
CONFIG_FILE=$DEF_CONFIG_FILE

# Used by show_help
HELP_MESSAGE="
  Installs the sample applications of Lightbend Fast Data Platform.
  Assumes DC/OS authentication was successful using the DC/OS CLI.
  The 'jq' JSON parser command must be installed!"
HELP_EXAMPLE_OPTIONS="--config-file ~/config.json"

# The ')' must be on the line AFTER the EOF!
HELP_OPTIONS=$(cat <<EOF
  -f | --config-file json-file     Configuration file used to launch applications
                              Default: $RELDIR/config.json
EOF
)

function parse_arguments {

  while :; do
    case "$1" in
      -f|--config-file)
      shift
      CONFIG_FILE=$1
      ;;
      -h|--help)   # Call a "show_help" function to display a synopsis, then exit.
      show_help
      exit 0
      ;;
      -n|--no-exec)   # Don't actually run the installation commands; just print (for debugging)
      NOEXEC="echo running: "
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

function get_components {
  local components=$( jq --arg app_name "$1" '.[] | select(.app == $app_name) | .components[]' "$CONFIG_FILE" )
  echo $components
}

## Install application with components specified in the config file
function install_application_components {
  local application="$1"
  local app_folder="$2"
  local app_description="$3"

  local components=$(get_components $application)

  # need to invoke the respective app-install script from the application's bin folder
  PROJ_BIN_DIR="$( cd "$DIR/../$app_folder/bin" && pwd -P )"

  if [ ! -z "$components" ]
  then
    echo "Components found in config [ $components ] .."

    # for each project for each component we invoke the script with --start-only component
    # e.g. for network intrusion application we invoke as:
    # app-install.sh --start-only transform-data --start-only anomaly-detection ..
    # Build this complete suffix here
    local suffix=$(make_command_suffix "$components")

    # need to remove quotes
    local suffix_no_quotes=$(echo $suffix | sed 's/"//g')

    # invoke installation script with selected components
    $NOEXEC $PROJ_BIN_DIR/app-install.sh $suffix_no_quotes
  else
    echo "No component found to install for $app_description application .. installing all available .."

    # invoke installation script
    $NOEXEC $PROJ_BIN_DIR/app-install.sh
  fi
}

## Install model server application with components specified in the config file
function install_model_server {
  local application="$1"
  local app_folder="$2"
  local app_description="$3"

  local components=$(get_components $application)

  # need to invoke the respective app-install script from the application's bin folder
  local proj_bin_dir="$( cd "$DIR/../$app_folder/bin" && pwd -P )"

  if [ ! -z "$components" ]
  then
    echo "Components found in config [ $components ] .."

    IFS=', ' read -r -a array <<< "$components"

    # invoke installation script with selected components
    for element in "${array[@]}"
    do
      local no_quote=$(remove_quotes $element)
      invoke_model_server_service $no_quote $proj_bin_dir
    done
  else
    echo "No component found to install for $app_description application .. installing all available .."

    for element in akka-stream-svc kafka-stream-svc publisher
    do
      invoke_model_server_service $element $proj_bin_dir
    done
  fi
}

function invoke_model_server_service {
  local proj_bin_dir="$2"

  case "$1" in
    "akka-stream-svc")
      $NOEXEC $proj_bin_dir/run-dcos-akkastreams-svc.sh
      ;;

    "kafka-stream-svc")
      $NOEXEC $proj_bin_dir/run-dcos-kafkastreams-svc.sh
      ;;

    "publisher")
      $NOEXEC $proj_bin_dir/run-dcos-publisher.sh
      ;;
  esac
}

function make_command_suffix {
  IFS=', ' read -r -a components <<< "$1"

  local suffix=""

  for component in "${components[@]}"
  do
    pair=$(make_pair "$component")
    suffix=$suffix$pair
  done
  echo $suffix
}

function make_pair {
  component="$1"
  local pair=" --start-only $component"
  echo "$pair"
}

## Install killrweather application with components specified in the config file
function install_killrweather {
  local application="$1"
  local app_folder="$2"
  local app_description="$3"

  local components=$(get_components $application)

  # need to invoke the respective app-install script from the application's bin folder
  local proj_bin_dir="$( cd "$DIR/../$app_folder/bin" && pwd -P )"

  # process templates
  echo Running process_templates to generate json from template files ..
  $proj_bin_dir/../process-templates.sh $VERSION

  if [ ! -z "$components" ]
  then
    echo "Components found in config [ $components ] .."

    IFS=', ' read -r -a array <<< "$components"

    # invoke installation script with selected components
    for element in "${array[@]}"
    do
      local no_quote=$(remove_quotes $element)
      invoke_killrweather_service $no_quote $proj_bin_dir
    done
  else
    echo "No component found to install for $app_description application .. installing all available .."

    for element in data-loader http-client grpc-client app structured-app
    do
      invoke_killrweather_service $element $proj_bin_dir
    done
  fi
}

function invoke_killrweather_service {
  local proj_bin_dir="$2"

  case "$1" in
    "data-loader")
      $NOEXEC dcos marathon pod add $proj_bin_dir/killrweatherloaderDocker.json
      ;;

    "http-client")
      $NOEXEC dcos marathon app add $proj_bin_dir/killrweatherHTTPClientDocker.json
      ;;

    "grpc-client")
      $NOEXEC dcos marathon app add $proj_bin_dir/killrweatherGRPCClientDocker.json
      ;;

    "app")
      $NOEXEC dcos marathon app add $proj_bin_dir/killrweatherAppDocker.json
      ;;

    "structured-app")
      $NOEXEC dcos marathon app add $proj_bin_dir/killrweatherApp_structuredDocker.json
      ;;
  esac
}

function main {

  parse_arguments "$@"

  require_jq

  [[ -f "$CONFIG_FILE" ]] || error "Config file $CONFIG_FILE not found. Try copying $DEF_CONFIG_FILE.template to $CONFIG_FILE."

  echo "Checking config file for components of Network Intrusion application ..."

  # install nwintrusion
  install_application_components nwintrusion nwintrusion "Network Intrusion"

  # install kstream
  install_application_components kstream kstream "Kafka Streams"

  # install flink taxiride
  install_application_components taxiride flink "NYC Taxi Ride"

  # install bigdl vggcifar
  install_application_components vggcifar bigdl "BigDL VGG training on Cifar10"

  # install model server
  install_model_server modelserver akka-kafka-streams-model-server "Akka / Kafka Streams Model Server"

  # install killrweather
  install_killrweather killrweather killrweather "Killrweather"
}

main "$@"
exit 0
