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

DEF_APP_LIST=(
  nwintrusion
  weblogs
  taxiride
  vggcifar
  modelserver
  killrweather)
APP_LIST=()

# Used by show_help
HELP_MESSAGE="
  Installs the sample applications of Lightbend Fast Data Platform.
  Assumes DC/OS authentication was successful using the DC/OS CLI."
HELP_EXAMPLE_OPTIONS="--config-file ~/config.json --app killrweather"

# The ')' must be on the line AFTER the EOF!
HELP_OPTIONS=$(cat <<EOF
  -f | --config-file file     Configuration file used to launch applications.
                              Default: $RELDIR/config.json.
  -a | --app app              Run this application. This option can be repeated.
                              Here is the list of apps. See the README for details:
$(for a in ${DEF_APP_LIST[@]}; do echo "                                $a"; done)
                              Default: all of them.
                              For apps with more than one process, edit apps/bin/config.json
                              to specify which ones to install, when there are choices, such
                              as for KillrWeather. See the app READMEs for details.
EOF
)

function parse_arguments {

  while :; do
    case "$1" in
      -f|--config-file)
        shift
        CONFIG_FILE=$1
        ;;
      -a|--app)
        shift
        APP_LIST+=($1)
        ;;
      -h|--help)   # Call a "show_help" function to display a synopsis, then exit.
        show_help
        exit 0
        ;;
      -n|--no-exec)   # Don't actually run the installation commands; just print (for debugging)
        NOEXEC="info running: "
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

  info
  info "*** Running application $application ($app_description)"

  # need to invoke the respective app-install script from the application's bin folder
  PROJ_BIN_DIR="$( cd "$DIR/../$app_folder/bin" && pwd -P )"

  if [ ! -z "$components" ]
  then
    info "Components found in config [ $components ] .."

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
    info "No component found to install for $app_description application .. installing all available .."

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

  info
  info "*** Running application $application ($app_description)"

  # need to invoke the respective app-install script from the application's bin folder
  local proj_bin_dir="$( cd "$DIR/../$app_folder/bin" && pwd -P )"

  if [ ! -z "$components" ]
  then
    info "Components found in config [ $components ] .."

    IFS=', ' read -r -a array <<< "$components"

    # invoke installation script with selected components
    for element in "${array[@]}"
    do
      local no_quote=$(remove_quotes $element)
      invoke_model_server_service $no_quote $proj_bin_dir
    done
  else
    info "No component found to install for $app_description application .. installing all available .."

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

  info
  info "*** Running application $application ($app_description)"

  # need to invoke the respective app-install script from the application's bin folder
  local proj_bin_dir="$( cd "$DIR/../$app_folder/bin" && pwd -P )"

  # process templates
  info Running process_templates to generate json from template files ..
  $proj_bin_dir/../process-templates.sh $VERSION

  if [ ! -z "$components" ]
  then
    info "Components found in config [ $components ] .."

    IFS=', ' read -r -a array <<< "$components"

    # invoke installation script with selected components
    for element in "${array[@]}"
    do
      local no_quote=$(remove_quotes $element)
      invoke_killrweather_service $no_quote $proj_bin_dir
    done
  else
    info "No component found to install for $app_description application .. installing all available .."

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

  # If the config file doesn't exist, but it's the default file, then copy over the template
  # and use that. Otherwise, it's an error if the file doesn't exist.
  if [[ ! -f "$CONFIG_FILE" ]]
  then
    if [[ $DEF_CONFIG_FILE = $CONFIG_FILE ]]
    then
      warn "$DEF_CONFIG_FILE not found. Copying $DEF_CONFIG_FILE.template to $CONFIG_FILE"
      cp $DEF_CONFIG_FILE.template $CONFIG_FILE
    else
      error "Config file $CONFIG_FILE not found. Try copying $DEF_CONFIG_FILE.template to $CONFIG_FILE."
    fi
  fi

  [[ ${#APP_LIST[@]} -eq 0 ]] && APP_LIST=( ${DEF_APP_LIST[@]} )

  info "Checking config file $CONFIG_FILE for the desired applications and running them..."

  for app in ${APP_LIST[@]}
  do
    case $app in
      nwintrusion)
        install_application_components nwintrusion nwintrusion "Network Intrusion"
        ;;
      weblogs)
        install_application_components kstream kstream "Kafka Streams Web Logs Analysis"
        ;;
      taxiride)
        install_application_components taxiride flink "NYC Taxi Ride with Flink"
        ;;
      vggcifar)
        install_application_components vggcifar bigdl "BigDL VGG training on CIFAR-10"
        ;;
      modelserver)
        install_model_server modelserver akka-kafka-streams-model-server "Akka / Kafka Streams Model Server"
        ;;
      killrweather)
        install_killrweather killrweather killrweather "Killrweather"
        ;;
      *)
        warn "Unknown application $app. Skipping..."
        ;;
    esac
  done
}

main "$@"
exit 0
