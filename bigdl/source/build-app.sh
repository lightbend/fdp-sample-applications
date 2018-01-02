#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../bin/common.sh"
. "$DIR/../version.sh"

# You could override these definitions with environment variables.
: ${S3_BUCKET:="fdp-sample-bigdl-vgg"}
: ${JAR:="bigdlsample-assembly-$APP_VERSION.jar"}
: ${AWS_ENV_FILE:=$HOME/.ssh/aws.sh}

# Used by show_help
HELP_MESSAGE="Builds and pushes all artifacts of this app to S3."
HELP_EXAMPLE_OPTIONS=

function parse_arguments {
  while [ $# -gt 0 ]; do
    case "$1" in
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
      --)          # End of all options.
      shift
      break
      ;;
      '')          # End of all options.
      break
      ;;
      *)
      error "The option is not valid: $1"
      ;;
    esac
    shift
  done
}

function upload_app_jar {
  cd "$DIR"
  echo "Building jar..."
  $NOEXEC sbt clean clean-files

  $NOEXEC sbt assembly

  echo "Uploading jar $JAR to S3 bucket $S3_BUCKET..."
  $NOEXEC aws s3 cp "$DIR/target/scala-2.11/$JAR" "s3://$S3_BUCKET/$JAR" --acl public-read --region "$AWS_DEFAULT_REGION"
}

function check_required_utils {
  type python >/dev/null 2>&1 || {
    echo >&2 "python is required but it's not installed. Installing python...";
    install_python
  }
  echo "$(type python)"

  type sbt >/dev/null 2>&1 || {
    echo >&2 "sbt is required but it's not installed. Installing sbt...";
    install_sbt
  }
  echo "$(type sbt)"

  type unzip >/dev/null 2>&1 || {
    echo >&2 "unzip is required but it's not installed. Installing unzip...";
    install_unzip
  }
  echo "$(type unzip)"

  type curl >/dev/null 2>&1 || {
    echo >&2 "curl is required but it's not installed. Installing curl...";
    install_curl
  }
  echo "$(type curl)"

  type aws >/dev/null 2>&1 || {
    echo >&2 "aws is required but it's not installed. Installing aws...";
    install_aws_cli
  }
  echo "$(type aws)"
}

function print_env_error {
  echo  "$1 not defined: $AWS_ENV_FILE either doesn't exist or it doesn't define $1."
  error "See https://developer.lightbend.com/docs/fast-data-platform/latest/user-guide/sample-apps/index.html for more information."
}

function check_required_vars {
  if [ -z $AWS_ACCESS_KEY_ID ]; then
    echo "AWS_ACCESS_KEY_ID not defined: Trying $AWS_ENV_FILE"
    [ -f $AWS_ENV_FILE ] && source $AWS_ENV_FILE
    if [ -z $AWS_ACCESS_KEY_ID ]; then
      print_env_error "AWS_ACCESS_KEY_ID"
    fi
  fi

  if [ -z $AWS_SECRET_ACCESS_KEY ]; then
    echo "AWS_SECRET_ACCESS_KEY not defined: Trying $AWS_ENV_FILE"
    [ -f $AWS_ENV_FILE ] && source $AWS_ENV_FILE
    if [ -z $AWS_SECRET_ACCESS_KEY ]; then
      print_env_error "AWS_SECRET_ACCESS_KEY"
    fi
  fi

  if [ -z $AWS_DEFAULT_REGION ]; then
    echo "AWS_DEFAULT_REGION not defined: Trying to acquire AWS_DEFAULT_REGION from EC2_AZ_REGION in $AWS_ENV_FILE"
    [ -f $AWS_ENV_FILE ] && source $AWS_ENV_FILE
    if [ -z $EC2_AZ_REGION ]; then
      error "EC2_AZ_REGION is not defined in $AWS_ENV_FILE. Please define AWS_DEFAULT_REGION to proceed."
    fi
    export AWS_DEFAULT_REGION="${EC2_AZ_REGION%?}"
  fi

  export AWS_DEFAULT_OUTPUT="json"
  # source is not enough for the aws cli to pick up the vars
  export AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID
  export AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY

  echo "Variables to use for the configuration of the aws cli command util..."
  echo "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY defined"
  echo "AWS_DEFAULT_REGION = $AWS_DEFAULT_REGION"
  echo "AWS_DEFAULT_OUTPUT = $AWS_DEFAULT_OUTPUT"
}

function main {
  parse_arguments "$@"
  check_required_utils
  check_required_vars

  echo "Using AWS_DEFAULT_REGION $AWS_DEFAULT_REGION and S3_BUCKET $S3_BUCKET for uploads"

  upload_app_jar
  echo "Artifacts built and pushed successfully!"
}

main "$@"
exit 0

