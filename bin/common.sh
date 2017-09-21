# Utilities and definitions shared by several scripts.

AWS_ENV_FILE=$HOME/.ssh/aws.sh

APP_METADATA_FILE="$DIR/app.metadata.json"
KAFKA_DCOS_PACKAGE="kafka"

# Used by --no-exec option
NOEXEC=

function show_help {
  cat<< EOF

  $HELP_MESSAGE

  Usage: $SCRIPT  $ARGS [options] $OPTIONAL_ARGS

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
  show_help
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
  KAFKA_CONN_INFO="$($NOEXEC dcos $KAFKA_DCOS_PACKAGE endpoints broker --name=$KAFKA_DCOS_SERVICE_NAME)"
  KAFKA_BROKERS="$(echo $KAFKA_CONN_INFO | $NOEXEC jq -r '.dns[0]')"
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

  dcos package list --cli --json | jq -e ".[] | select(.command.name == \"$KAFKA_DCOS_PACKAGE\")" > /dev/null 2>&1 || {
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
  if [[ $(dcos spark 2>&1) == *"not a dcos command"* ]]; then
    error "DC/OS spark CLI subcommand is required but it is not installed. Please install and retry..."
  fi
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

function install_aws_cli {
  curl "https://s3.amazonaws.com/aws-cli/awscli-bundle.zip" -o "awscli-bundle.zip"
  unzip awscli-bundle.zip
  sudo ./awscli-bundle/install -i /usr/local/aws -b /usr/local/bin/aws
}

function install_python {
  case $(uname) in
    Linux*)
    sudo apt-get install python
    ;;
    Darwin*)
    brew install python
    ;;
  *)
  echo "Unrecognized OS (output of uname = $(uname)). Please install unzip."
  exit 1
  ;;
  esac
}

function install_sbt {
  case $(uname) in
    Linux*)
    echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
    sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
    sudo apt-get update
    sudo apt-get install sbt
    ;;
    Darwin*)
    brew install sbt
    ;;
  *)
  echo "Unrecognized OS (output of uname = $(uname)). Please install sbt."
  exit 1
  ;;
  esac
}

function install_unzip {
  case $(uname) in
    Linux*)
    sudo apt-get install unzip
    ;;
    Darwin*)
    brew install unzip
    ;;
  *)
  echo "Unrecognized OS (output of uname = $(uname)). Please install unzip."
  exit 1
  ;;
  esac
}

function install_curl {
  case $(uname) in
    Linux*)
    sudo apt-get install curl
    ;;
    Darwin*)
    brew install curl
    ;;
  *)
  echo "Unrecognized OS (output of uname = $(uname)). Please install curl."
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

# Prompts the user to choose one item from an array. The items are printed one
# per line with a number in front. The user selects the number. The first item
# is treated as the default, if the user just hits "return".
#
# $1 - Name of the thing being chosen for a leading message.
# $2..$# items to be chosen from.
function prompt_for_choice {
  name_of_item=$1
  shift
  vals=("$@")

  echo2 "Enter a number and <return> to select the $name_of_item to use:"
  for i in "${!vals[@]}"
  do
    let i1=i+1
    default=
    [ $i -eq 0 ] && default="(default)"
    printf2 "%2d: %s %s\n" $i1 ${vals[$i]} "$default"
  done
  read i2
  [ -z "$i2" ] && let i2=1
  if [ $i2 -le 0 -o $i2 -gt ${#vals[@]} ]
  then
    error "Must specify a number between 1 and ${#vals[@]}"
  fi
  let i21=i2-1
  echo "${vals[$i21]}"
}

# Echo the input to stderr instead of stdout
function echo2 {
  echo "$@" >&2
}

# Print the input to stderr instead of stdout
function printf2 {
  printf "$@" >&2
}
