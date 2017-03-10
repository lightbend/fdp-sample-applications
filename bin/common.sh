# Utilities and definitions shared by several scripts.

AWS_ENV_FILE=$HOME/.ssh/aws.sh

APP_METADATA_FILE="$DIR/app.metadata.json"
KAFKA_DCOS_PACKAGE="kafka"

# Used by --no-exec option
NOEXEC=

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
  usage
  exit 1
}

function warn {
  echo >&2
  echo "WARN: " >&2
  for s in "$@"
  do
    echo "  $s" >&2
    shift
  done
  echo >&2
}

function header {
  printf "\n=== $@"
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

function require_jq {
  set +e
  type jq >/dev/null 2>&1 || install_jq
  set -e
}

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

function show_submission_message {
  echo "Submission ID: $1"
  echo "To tail the logs, run this DC/OS CLI command:"
  echo "  dcos spark log $1 --follow"
}
